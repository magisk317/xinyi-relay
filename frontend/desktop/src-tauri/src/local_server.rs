use std::net::SocketAddr;
use std::sync::Arc;

use axum::extract::{DefaultBodyLimit, State as AxumState};
use axum::http::{HeaderMap, StatusCode};
use axum::response::Json;
use axum::routing::{get, post};
use axum::Router;
use serde_json::{json, Value};
use tokio::net::TcpListener;

use crate::sqlite_store::SqliteStore;
use crate::store::{Record, Store, StoreError};

const MAX_STRING_LEN: usize = 512;
const MAX_RECORDS_PER_BATCH: usize = 200;
const MAX_REQUEST_BODY_BYTES: usize = 8 << 20;

#[derive(Clone)]
struct LocalServerState {
    store: Arc<SqliteStore>,
}

type HttpError = (StatusCode, Json<Value>);

fn sanitize(value: &str, max_len: usize) -> String {
    value.chars().take(max_len).collect()
}

fn bad_request(message: &str) -> HttpError {
    (StatusCode::BAD_REQUEST, Json(json!({"error": message})))
}

fn map_store_err(error: StoreError) -> HttpError {
    match error {
        StoreError::Conflict { local, remote } => (
            StatusCode::CONFLICT,
            Json(json!({"error": "conflict", "local": local, "remote": remote})),
        ),
        StoreError::Internal(message) => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({"error": message})),
        ),
    }
}

fn bearer_token(headers: &HeaderMap) -> Option<&str> {
    headers
        .get("authorization")
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.strip_prefix("Bearer "))
        .filter(|value| !value.is_empty())
}

fn authenticate(state: &LocalServerState, headers: &HeaderMap) -> Result<i64, HttpError> {
    let token = bearer_token(headers).ok_or_else(|| {
        (
            StatusCode::UNAUTHORIZED,
            Json(json!({"error": "missing device token"})),
        )
    })?;
    state
        .store
        .authenticate_local_device(token)
        .map_err(map_store_err)?
        .ok_or_else(|| {
            (
                StatusCode::UNAUTHORIZED,
                Json(json!({"error": "invalid or revoked device token"})),
            )
        })
}

async fn handle_register(
    AxumState(state): AxumState<LocalServerState>,
    Json(body): Json<Value>,
) -> Result<(StatusCode, Json<Value>), HttpError> {
    let bind_code = body["bindCode"].as_str().unwrap_or("").trim();
    let device_name = sanitize(
        body["deviceName"].as_str().unwrap_or("").trim(),
        MAX_STRING_LEN,
    );
    if bind_code.is_empty() || device_name.is_empty() {
        return Err(bad_request("bindCode and deviceName are required"));
    }
    let device_token = uuid::Uuid::new_v4().to_string();
    let device = state
        .store
        .register_local_device(
            bind_code,
            &device_name,
            &sanitize(body["deviceModel"].as_str().unwrap_or(""), MAX_STRING_LEN),
            &sanitize(body["platform"].as_str().unwrap_or("android"), 32),
            &sanitize(body["appVersion"].as_str().unwrap_or(""), 64),
            &device_token,
        )
        .map_err(map_store_err)?
        .ok_or_else(|| {
            (
                StatusCode::UNAUTHORIZED,
                Json(json!({"error": "invalid or expired bind code"})),
            )
        })?;
    Ok((
        StatusCode::CREATED,
        Json(json!({
            "userId": device.user_id,
            "deviceId": device.id,
            "deviceToken": device_token,
        })),
    ))
}

async fn handle_heartbeat(
    AxumState(state): AxumState<LocalServerState>,
    headers: HeaderMap,
    Json(body): Json<Value>,
) -> Result<Json<Value>, HttpError> {
    let device_id = authenticate(&state, &headers)?;
    state
        .store
        .update_local_device_heartbeat(
            device_id,
            body["appVersion"].as_str().unwrap_or(""),
            &body["localAddresses"],
            &body["capabilities"],
        )
        .map_err(map_store_err)?;
    Ok(Json(json!({"ok": true})))
}

async fn handle_config_mirror(
    AxumState(state): AxumState<LocalServerState>,
    headers: HeaderMap,
    Json(body): Json<Value>,
) -> Result<Json<Value>, HttpError> {
    let device_id = authenticate(&state, &headers)?;
    let revision = body["localRevision"]
        .as_i64()
        .ok_or_else(|| bad_request("localRevision is required"))?;
    let snapshot = body["mirrorContent"].clone();
    let config = state
        .store
        .upsert_device_config_mirror(device_id, revision, snapshot, None)
        .map_err(map_store_err)?;
    Ok(Json(json!(config)))
}

async fn handle_config_pull(
    AxumState(state): AxumState<LocalServerState>,
    headers: HeaderMap,
    Json(body): Json<Value>,
) -> Result<Json<Value>, HttpError> {
    let device_id = authenticate(&state, &headers)?;
    let local_revision = body["localRevision"].as_i64().unwrap_or(0);
    let config = state
        .store
        .get_device_config(device_id)
        .map_err(map_store_err)?
        .ok_or_else(|| {
            (
                StatusCode::NOT_FOUND,
                Json(json!({"error": "device config not found"})),
            )
        })?;
    let mirror_content = if local_revision > 0 && local_revision >= config.revision {
        json!({})
    } else {
        config.snapshot
    };
    Ok(Json(json!({
        "deviceId": device_id,
        "revision": config.revision,
        "mirrorContent": mirror_content,
        "pendingCommands": config.pending_commands,
        "updatedAt": config.updated_at.unwrap_or_default(),
    })))
}

async fn handle_config_ack(
    AxumState(state): AxumState<LocalServerState>,
    headers: HeaderMap,
    Json(body): Json<Value>,
) -> Result<Json<Value>, HttpError> {
    let device_id = authenticate(&state, &headers)?;
    let command = state
        .store
        .ack_local_device_config_command(
            device_id,
            body["commandId"]
                .as_i64()
                .ok_or_else(|| bad_request("commandId is required"))?,
            body["status"].as_str().unwrap_or("applied"),
            body["appliedRevision"].as_i64().unwrap_or(0),
            body["failureReason"].as_str().unwrap_or(""),
            &body["mirrorContent"],
        )
        .map_err(map_store_err)?;
    Ok(Json(json!(command)))
}

async fn handle_upload_records(
    AxumState(state): AxumState<LocalServerState>,
    headers: HeaderMap,
    Json(body): Json<Value>,
) -> Result<Json<Value>, HttpError> {
    let device_id = authenticate(&state, &headers)?;
    let source = body["records"]
        .as_array()
        .ok_or_else(|| bad_request("records must be an array"))?;
    if source.len() > MAX_RECORDS_PER_BATCH {
        return Err(bad_request("too many records"));
    }
    let uploaded_at = chrono::Utc::now().to_rfc3339();
    let records = source
        .iter()
        .map(|record| Record {
            id: 0,
            device_id,
            event_id: record["eventId"].as_str().map(|value| sanitize(value, 128)),
            record_type: sanitize(record["recordType"].as_str().unwrap_or("sms"), 32),
            sender: sanitize(record["sender"].as_str().unwrap_or(""), MAX_STRING_LEN),
            body: sanitize(record["body"].as_str().unwrap_or(""), 4096),
            sms_code: sanitize(record["smsCode"].as_str().unwrap_or(""), 32),
            package_name: sanitize(record["packageName"].as_str().unwrap_or(""), 256),
            metadata: record["metadata"].clone(),
            msg_type: record["msgType"].as_i64().unwrap_or(0) as i32,
            call_type: record["callType"].as_i64().unwrap_or(0) as i32,
            occurred_at: sanitize(record["occurredAt"].as_str().unwrap_or(""), 64),
            uploaded_at: uploaded_at.clone(),
        })
        .collect::<Vec<_>>();
    let result = state
        .store
        .sync_local_device_records(
            device_id,
            &records,
            body["replaceExisting"].as_bool().unwrap_or(false),
        )
        .map_err(map_store_err)?;
    Ok(Json(json!({
        "inserted": result.inserted,
        "updated": result.updated,
        "deleted": result.deleted,
    })))
}

async fn handle_system_info(
    AxumState(state): AxumState<LocalServerState>,
) -> Result<Json<Value>, HttpError> {
    let info = state.store.get_system_info().map_err(map_store_err)?;
    Ok(Json(json!(info)))
}

fn local_server_router(store: Arc<SqliteStore>) -> Router {
    Router::new()
        .route("/api/v1/system/info", get(handle_system_info))
        .route("/api/v1/agent/register", post(handle_register))
        .route("/api/v1/agent/heartbeat", post(handle_heartbeat))
        .route("/api/v1/agent/config/mirror", post(handle_config_mirror))
        .route(
            "/api/v1/agent/config/commands:pull",
            post(handle_config_pull),
        )
        .route("/api/v1/agent/config/commands:ack", post(handle_config_ack))
        .route("/api/v1/agent/records:batch", post(handle_upload_records))
        .route("/healthz", get(|| async { "ok" }))
        .layer(DefaultBodyLimit::max(MAX_REQUEST_BODY_BYTES))
        .with_state(LocalServerState { store })
}

pub async fn start_local_server(store: Arc<SqliteStore>) -> Result<SocketAddr, String> {
    // Device payloads contain bearer tokens and message bodies. Until this
    // embedded server has a real TLS identity, it must never listen on LAN.
    let listener = TcpListener::bind(("127.0.0.1", 0))
        .await
        .map_err(|error| format!("Failed to bind local server: {error}"))?;
    let address = listener
        .local_addr()
        .map_err(|error| format!("Failed to get local address: {error}"))?;
    tokio::spawn(async move {
        if let Err(error) = axum::serve(listener, local_server_router(store)).await {
            eprintln!("Local server error: {error}");
        }
    });
    Ok(address)
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::{to_bytes, Body};
    use axum::http::Request;
    use tower::ServiceExt;

    async fn json_body(response: axum::response::Response) -> Value {
        let body = to_bytes(response.into_body(), MAX_REQUEST_BODY_BYTES)
            .await
            .unwrap();
        serde_json::from_slice(&body).unwrap()
    }

    #[tokio::test]
    async fn agent_registration_requires_a_single_use_desktop_bind_code() {
        let store = Arc::new(SqliteStore::open_in_memory().unwrap());
        let bind_code = store.create_bind_code().unwrap().code;
        let router = local_server_router(store);
        let payload = json!({
            "bindCode": bind_code,
            "deviceName": "Phone",
            "deviceModel": "Pixel",
            "platform": "android",
            "appVersion": "1.0",
        });
        let request = || {
            Request::post("/api/v1/agent/register")
                .header("content-type", "application/json")
                .body(Body::from(payload.to_string()))
                .unwrap()
        };

        let registered = router.clone().oneshot(request()).await.unwrap();
        assert_eq!(registered.status(), StatusCode::CREATED);
        let response = json_body(registered).await;
        assert!(response["deviceToken"].as_str().unwrap().len() >= 32);

        let replayed = router.oneshot(request()).await.unwrap();
        assert_eq!(replayed.status(), StatusCode::UNAUTHORIZED);
    }

    #[tokio::test]
    async fn agent_routes_reject_missing_device_token() {
        let store = Arc::new(SqliteStore::open_in_memory().unwrap());
        let response = local_server_router(store)
            .oneshot(
                Request::post("/api/v1/agent/config/commands:pull")
                    .header("content-type", "application/json")
                    .body(Body::from(r#"{"localRevision":0}"#))
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    }
}
