use std::collections::HashMap;
use std::net::SocketAddr;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use axum::extract::State as AxumState;
use axum::http::StatusCode;
use axum::response::Json;
use axum::routing::{get, post, put};
use axum::Router;
use serde_json::{json, Value};
use tokio::net::TcpListener;
use tower_http::cors::{AllowOrigin, CorsLayer};

use crate::store::{Store, StoreError};

const MAX_STRING_LEN: usize = 512;
const MAX_RECORDS_PER_BATCH: usize = 200;
const MAX_QUERY_LIMIT: i64 = 500;
const RATE_LIMIT_WINDOW: Duration = Duration::from_secs(60);
const RATE_LIMIT_MAX_REGISTERS: usize = 5;

#[derive(Clone)]
struct LocalServerState {
    store: Arc<Mutex<dyn Store + Send>>,
    device_tokens: Arc<Mutex<Vec<(String, i64)>>>,
    rate_limiter: Arc<Mutex<RateLimiter>>,
}

struct RateLimiter {
    window_start: Instant,
    count: usize,
}

impl RateLimiter {
    fn new() -> Self {
        Self {
            window_start: Instant::now(),
            count: 0,
        }
    }

    fn check_and_record(&mut self) -> bool {
        let now = Instant::now();
        if now.duration_since(self.window_start) > RATE_LIMIT_WINDOW {
            self.window_start = now;
            self.count = 0;
        }
        if self.count >= RATE_LIMIT_MAX_REGISTERS {
            return false;
        }
        self.count += true as usize;
        true
    }
}

fn sanitize(s: &str, max_len: usize) -> String {
    s.chars().take(max_len).collect()
}

fn auth_device(
    state: &LocalServerState,
    headers: &axum::http::HeaderMap,
) -> Result<i64, (StatusCode, Json<Value>)> {
    let token = headers
        .get("authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "))
        .unwrap_or("");
    if token.is_empty() {
        return Err((StatusCode::UNAUTHORIZED, Json(json!({"error": "missing token"}))));
    }
    let tokens = state.device_tokens.lock().unwrap();
    tokens
        .iter()
        .find(|(t, _)| t == token)
        .map(|(_, id)| *id)
        .ok_or_else(|| (StatusCode::UNAUTHORIZED, Json(json!({"error": "invalid token"}))))
}

async fn handle_register(
    AxumState(state): AxumState<LocalServerState>,
    Json(body): Json<Value>,
) -> Result<Json<Value>, (StatusCode, Json<Value>)> {
    // Rate limiting
    if !state.rate_limiter.lock().unwrap().check_and_record() {
        return Err((StatusCode::TOO_MANY_REQUESTS, Json(json!({"error": "rate limit exceeded"}))));
    }

    let device_name = sanitize(body["deviceName"].as_str().unwrap_or("Unknown"), MAX_STRING_LEN);
    let device_model = sanitize(body["deviceModel"].as_str().unwrap_or(""), MAX_STRING_LEN);
    let platform = sanitize(body["platform"].as_str().unwrap_or("android"), 32);
    let app_version = sanitize(body["appVersion"].as_str().unwrap_or(""), 64);

    let store = state.store.lock().unwrap();
    let devices = store.list_devices().map_err(map_store_err)?;
    let new_id = devices.iter().map(|d| d.id).max().unwrap_or(0) + 1;
    let token = uuid::Uuid::new_v4().to_string();

    store.upsert_devices(vec![crate::store::Device {
        id: new_id,
        user_id: 1,
        device_name,
        device_model,
        platform,
        app_version,
        display_name: String::new(),
        enabled: true,
        revoked_at: None,
        last_seen_at: Some(chrono::Utc::now().to_rfc3339()),
        local_addresses: json!([]),
        capabilities: json!({}),
        created_at: chrono::Utc::now().to_rfc3339(),
        updated_at: chrono::Utc::now().to_rfc3339(),
    }]).map_err(map_store_err)?;

    state.device_tokens.lock().unwrap().push((token.clone(), new_id));

    Ok(Json(json!({ "userId": 1, "deviceId": new_id, "deviceToken": token })))
}

async fn handle_heartbeat(
    AxumState(state): AxumState<LocalServerState>,
    headers: axum::http::HeaderMap,
) -> Result<Json<Value>, (StatusCode, Json<Value>)> {
    let _device_id = auth_device(&state, &headers)?;
    Ok(Json(json!({ "status": "ok" })))
}

async fn handle_upload_records(
    AxumState(state): AxumState<LocalServerState>,
    headers: axum::http::HeaderMap,
    Json(body): Json<Value>,
) -> Result<Json<Value>, (StatusCode, Json<Value>)> {
    let device_id = auth_device(&state, &headers)?;
    let store = state.store.lock().unwrap();

    let records_array = body["records"].as_array().ok_or_else(|| {
        (StatusCode::BAD_REQUEST, Json(json!({"error": "records must be an array"})))
    })?;

    if records_array.len() > MAX_RECORDS_PER_BATCH {
        return Err((StatusCode::BAD_REQUEST, Json(json!({"error": "too many records"}))));
    }

    let records: Vec<crate::store::Record> = records_array
        .iter()
        .filter_map(|r| {
            Some(crate::store::Record {
                id: 0,
                device_id,
                event_id: r["eventId"].as_str().map(|s| sanitize(s, 128)),
                record_type: sanitize(r["recordType"].as_str().unwrap_or("sms"), 32),
                sender: sanitize(r["sender"].as_str().unwrap_or(""), MAX_STRING_LEN),
                body: sanitize(r["body"].as_str().unwrap_or(""), 4096),
                sms_code: sanitize(r["smsCode"].as_str().unwrap_or(""), 32),
                package_name: sanitize(r["packageName"].as_str().unwrap_or(""), 256),
                metadata: r["metadata"].clone(),
                msg_type: r["msgType"].as_i64().unwrap_or(0) as i32,
                call_type: r["callType"].as_i64().unwrap_or(0) as i32,
                occurred_at: sanitize(r["occurredAt"].as_str().unwrap_or(""), 64),
                uploaded_at: chrono::Utc::now().to_rfc3339(),
            })
        })
        .collect();

    let count = records.len();
    store.upsert_records(records).map_err(map_store_err)?;
    Ok(Json(json!({ "inserted": count, "status": "ok" })))
}

async fn handle_get_config(
    AxumState(state): AxumState<LocalServerState>,
    headers: axum::http::HeaderMap,
) -> Result<Json<Value>, (StatusCode, Json<Value>)> {
    let _device_id = auth_device(&state, &headers)?;
    let store = state.store.lock().unwrap();
    let snapshot = store.get_config_snapshot().map_err(map_store_err)?;
    match snapshot {
        Some(snap) => Ok(Json(json!({ "revision": snap.revision, "snapshot": snap.snapshot, "updatedAt": snap.updated_at }))),
        None => Ok(Json(json!(null))),
    }
}

async fn handle_put_config(
    AxumState(state): AxumState<LocalServerState>,
    headers: axum::http::HeaderMap,
    Json(body): Json<Value>,
) -> Result<Json<Value>, (StatusCode, Json<Value>)> {
    let _device_id = auth_device(&state, &headers)?;
    let store = state.store.lock().unwrap();
    let base_revision = body["baseRevision"].as_i64().unwrap_or(0);
    let snapshot = body["snapshot"].clone();

    match store.put_config_snapshot(base_revision, snapshot) {
        Ok(snap) => Ok(Json(json!({ "revision": snap.revision, "snapshot": snap.snapshot, "updatedAt": snap.updated_at }))),
        Err(StoreError::Conflict { local: _, remote }) => {
            Err((StatusCode::CONFLICT, Json(json!({
                "revision": remote,
                "snapshot": store.get_config_snapshot().ok().flatten().map(|s| s.snapshot).unwrap_or(json!({})),
                "error": "conflict"
            }))))
        }
        Err(e) => Err(map_store_err(e)),
    }
}

async fn handle_get_devices(
    AxumState(state): AxumState<LocalServerState>,
    headers: axum::http::HeaderMap,
) -> Result<Json<Value>, (StatusCode, Json<Value>)> {
    let _device_id = auth_device(&state, &headers)?;
    let store = state.store.lock().unwrap();
    let devices = store.list_devices().map_err(map_store_err)?;
    Ok(Json(json!({ "devices": devices })))
}

async fn handle_get_records(
    AxumState(state): AxumState<LocalServerState>,
    headers: axum::http::HeaderMap,
    axum::extract::Query(params): axum::extract::Query<HashMap<String, String>>,
) -> Result<Json<Value>, (StatusCode, Json<Value>)> {
    let _device_id = auth_device(&state, &headers)?;
    let store = state.store.lock().unwrap();
    let limit = params
        .get("limit")
        .and_then(|v| v.parse::<i64>().ok())
        .unwrap_or(50)
        .min(MAX_QUERY_LIMIT);
    let device_filter = params.get("device_id").and_then(|v| v.parse::<i64>().ok());
    let records = store.list_records(limit as i32, device_filter).map_err(map_store_err)?;
    Ok(Json(json!({ "records": records.items, "total": records.items.len() })))
}

async fn handle_system_info() -> Json<Value> {
    Json(json!({
        "service": "xinyi-relay-desktop-local",
        "version": env!("CARGO_PKG_VERSION"),
        "localBaseUrl": "local://sqlite",
        "databaseReady": true,
    }))
}

fn map_store_err(e: StoreError) -> (StatusCode, Json<Value>) {
    match e {
        StoreError::Conflict { local, remote } => (
            StatusCode::CONFLICT,
            Json(json!({"error": "conflict", "local": local, "remote": remote})),
        ),
        StoreError::NotFound => (StatusCode::NOT_FOUND, Json(json!({"error": "not found"}))),
        StoreError::Internal(msg) => (StatusCode::INTERNAL_SERVER_ERROR, Json(json!({"error": msg}))),
    }
}

pub async fn start_local_server(
    store: Arc<Mutex<dyn Store + Send>>,
) -> Result<SocketAddr, String> {
    let state = LocalServerState {
        store,
        device_tokens: Arc::new(Mutex::new(Vec::new())),
        rate_limiter: Arc::new(Mutex::new(RateLimiter::new())),
    };

    // Restrict CORS to Tauri app origins only
    let cors = CorsLayer::new()
        .allow_origin(AllowOrigin::list([
            "tauri://localhost".parse().unwrap(),
            "https://tauri.localhost".parse().unwrap(),
            "http://tauri.localhost".parse().unwrap(),
        ]))
        .allow_methods([
            axum::http::Method::GET,
            axum::http::Method::POST,
            axum::http::Method::PUT,
        ])
        .allow_headers([
            axum::http::header::AUTHORIZATION,
            axum::http::header::CONTENT_TYPE,
        ]);

    let app = Router::new()
        .route("/api/v1/system/info", get(handle_system_info))
        .route("/api/v1/devices/register", post(handle_register))
        .route("/api/v1/devices", get(handle_get_devices))
        .route("/api/v1/heartbeat", post(handle_heartbeat))
        .route("/api/v1/records/batch", post(handle_upload_records))
        .route("/api/v1/records", get(handle_get_records))
        .route("/api/v1/config/snapshot", get(handle_get_config))
        .route("/api/v1/config/snapshot", put(handle_put_config))
        .route("/healthz", get(|| async { "ok" }))
        .layer(cors)
        .with_state(state);

    let listener = TcpListener::bind("0.0.0.0:0")
        .await
        .map_err(|e| format!("Failed to bind local server: {}", e))?;
    let addr = listener
        .local_addr()
        .map_err(|e| format!("Failed to get local address: {}", e))?;

    tokio::spawn(async move {
        if let Err(e) = axum::serve(listener, app).await {
            eprintln!("Local server error: {}", e);
        }
    });

    Ok(addr)
}
