#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod desktop_i18n;
mod logger;
mod remote_store;
mod sqlite_store;
mod storage;
mod store;
mod sync;
mod tray;

use chrono::{DateTime, Utc};
use keyring_core::{Entry, Error as KeyringError};
use reqwest::{Client, Method, StatusCode, Url};
use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::BTreeMap;
use std::fs;
use std::io::{Read, Write};
use std::net::TcpListener;
#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;
use std::path::PathBuf;
use std::process::{Command, Stdio};
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Mutex,
};
use std::time::Duration;
use tauri::{AppHandle, Emitter, Manager, State, WindowEvent};
use tauri_plugin_notification::NotificationExt;
use uuid::Uuid;

use desktop_i18n::{
    is_chinese_language, localized_app_name, localized_backend_login_hint,
    localized_brand_name, localized_connected_message, localized_connected_summary,
    localized_probe_success_message, localized_record_summary, localized_runtime_message,
    localized_runtime_text, system_language_tag,
};
use remote_store::RemoteStore;
use sqlite_store::SqliteStore;
use storage::{
    active_profile, current_active_profile, delete_session_for_profile, ensure_directory,
    load_persisted_state, load_session_for_profile, persist_state_to_disk,
    save_session_for_profile,
};
use store::Store;
use tray::{setup_tray, show_main_window};

const SERVICE_NAME: &str = "io.github.magisk317.relay.desktop";
const DEFAULT_PROFILE_NAME: &str = "Local Docker Backend";
const DEFAULT_BACKEND_URL: &str = "https://localhost:8443";

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopProfile {
    id: String,
    name: String,
    base_url: String,
    allow_self_signed: bool,
    trusted_fingerprint: Option<String>,
    trusted_issuer: Option<String>,
    active: bool,
    last_connected_at: Option<String>,
    note: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopNotificationPreferences {
    enabled: bool,
    connection: bool,
    records: bool,
    devices: bool,
}

impl Default for DesktopNotificationPreferences {
    fn default() -> Self {
        Self {
            enabled: true,
            connection: true,
            records: true,
            devices: true,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopPersistedState {
    profiles: Vec<DesktopProfile>,
    notifications: DesktopNotificationPreferences,
    #[serde(default)]
    run_mode: RunMode,
}

impl Default for DesktopPersistedState {
    fn default() -> Self {
        Self {
            profiles: vec![DesktopProfile {
                id: Uuid::new_v4().to_string(),
                name: DEFAULT_PROFILE_NAME.to_string(),
                base_url: DEFAULT_BACKEND_URL.to_string(),
                allow_self_signed: true,
                trusted_fingerprint: None,
                trusted_issuer: Some("Local Docker + Caddy".to_string()),
                active: true,
                last_connected_at: None,
                note: Some("Default profile for a local Docker backend.".to_string()),
            }],
            notifications: DesktopNotificationPreferences::default(),
            run_mode: RunMode::Remote,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopSessionSecrets {
    profile_id: String,
    username: String,
    access_token: String,
    refresh_token: String,
    expires_at: String,
    refresh_expires_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopSessionState {
    authenticated: bool,
    username: Option<String>,
    expires_at: Option<String>,
    refresh_expires_at: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopConnectionSnapshot {
    state: String,
    message: String,
    last_changed_at: Option<String>,
}

impl From<store::Device> for DeviceItem {
    fn from(d: store::Device) -> Self {
        DeviceItem {
            id: d.id,
            user_id: d.user_id,
            device_name: d.device_name,
            device_model: d.device_model,
            platform: d.platform,
            app_version: d.app_version,
            display_name: d.display_name,
            enabled: d.enabled,
            revoked_at: d.revoked_at,
            last_seen_at: d.last_seen_at,
            local_addresses: d.local_addresses,
            capabilities: d.capabilities,
            created_at: d.created_at,
            updated_at: d.updated_at,
        }
    }
}

impl From<store::Record> for RecordItem {
    fn from(r: store::Record) -> Self {
        RecordItem {
            id: r.id,
            device_id: r.device_id,
            event_id: r.event_id,
            record_type: r.record_type,
            sender: r.sender,
            body: r.body,
            sms_code: r.sms_code,
            package_name: r.package_name,
            metadata: r.metadata,
            msg_type: r.msg_type,
            call_type: r.call_type,
            occurred_at: r.occurred_at,
            uploaded_at: r.uploaded_at,
        }
    }
}

impl From<store::ConfigAuditLog> for ConfigAuditLogItem {
    fn from(l: store::ConfigAuditLog) -> Self {
        ConfigAuditLogItem {
            id: l.id,
            revision: l.revision,
            actor_type: l.actor_type,
            actor_id: l.actor_id,
            summary: l.summary,
            created_at: l.created_at,
        }
    }
}

impl Default for DesktopConnectionSnapshot {
    fn default() -> Self {
        let language_tag = system_language_tag();
        Self {
            state: "connecting".to_string(),
            message: localized_runtime_text(&language_tag, "initializing").to_string(),
            last_changed_at: Some(now_rfc3339()),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopBootstrapState {
    app_version: String,
    platform: String,
    language_tag: String,
    profiles: Vec<DesktopProfile>,
    session: DesktopSessionState,
    notifications: DesktopNotificationPreferences,
    connection: DesktopConnectionSnapshot,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopBackendProbe {
    reachable: bool,
    allow_self_signed: bool,
    certificate_status: String,
    message: String,
    system_info: Option<SystemInfoState>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopAuthStart {
    auth_url: String,
    state: String,
    callback_url: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopAuthCallbackPayload {
    state: String,
    code: Option<String>,
    error: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopAuthExchangeResponse {
    authenticated: bool,
    username: String,
    access_token: String,
    refresh_token: String,
    expires_at: String,
    refresh_expires_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopDiagnosticsExport {
    path: String,
    created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SystemInfoState {
    service: String,
    app_env: String,
    local_base_url: String,
    public_base_url: String,
    database_ready: bool,
    user_count: i64,
    time: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DeviceItem {
    id: i64,
    user_id: i64,
    device_name: String,
    device_model: String,
    platform: String,
    app_version: String,
    display_name: String,
    enabled: bool,
    revoked_at: Option<String>,
    last_seen_at: Option<String>,
    local_addresses: Value,
    capabilities: Value,
    created_at: String,
    updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DevicesResponse {
    devices: Vec<DeviceItem>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct BindCodeResponse {
    code: String,
    expires_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RecordItem {
    id: i64,
    device_id: i64,
    event_id: Option<String>,
    record_type: String,
    sender: String,
    body: String,
    sms_code: String,
    package_name: String,
    metadata: Value,
    msg_type: i32,
    call_type: i32,
    occurred_at: String,
    uploaded_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RecordsResponse {
    records: Vec<RecordItem>,
    limit: i32,
    offset: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ConfigSnapshotState {
    revision: i64,
    snapshot: Value,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ConfigAuditLogItem {
    id: i64,
    revision: i64,
    actor_type: String,
    actor_id: i64,
    summary: String,
    created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ConfigAuditLogsResponse {
    logs: Vec<ConfigAuditLogItem>,
    limit: i32,
    offset: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RealtimeEvent {
    r#type: String,
    time: String,
    data: Option<Value>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
struct SaveProfileInput {
    id: Option<String>,
    name: String,
    base_url: String,
    allow_self_signed: bool,
    note: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PatchDeviceInput {
    display_name: Option<String>,
    enabled: Option<bool>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct AuthExchangeRequestBody<'a> {
    code: &'a str,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct RefreshRequestBody<'a> {
    refresh_token: &'a str,
}

#[derive(Debug, Clone, Serialize)]
struct ConfigSnapshotPutBody {
    #[serde(rename = "base_revision")]
    base_revision: i64,
    snapshot: Value,
}

#[derive(Debug, Clone, Deserialize)]
struct ErrorPayload {
    error: Option<String>,
}

#[derive(Debug, Clone)]
struct AuthFlow {
    profile_id: String,
    state: String,
}

#[derive(Debug, Clone, Default, PartialEq, Eq)]
struct MonitorDeviceSnapshot {
    display_name: String,
    enabled: bool,
    revoked_at: Option<String>,
    last_seen_at: Option<String>,
    updated_at: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct MonitorRecordSnapshot {
    id: i64,
    record_type: String,
    sender: String,
    sms_code: String,
}

#[derive(Debug, Clone, Default, PartialEq, Eq)]
struct MonitorSnapshot {
    devices: BTreeMap<i64, MonitorDeviceSnapshot>,
    latest_record: Option<MonitorRecordSnapshot>,
    config_revision: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
enum RunMode {
    Local,
    Remote,
    Hybrid,
}

impl Default for RunMode {
    fn default() -> Self {
        RunMode::Remote
    }
}

struct DesktopAppState {
    persisted: Mutex<DesktopPersistedState>,
    session: Mutex<Option<DesktopSessionSecrets>>,
    auth_flow: Mutex<Option<AuthFlow>>,
    connection: Mutex<DesktopConnectionSnapshot>,
    last_monitor: Mutex<MonitorSnapshot>,
    monitor_generation: AtomicU64,
    run_mode: Mutex<RunMode>,
    local_store: Mutex<Option<SqliteStore>>,
}

#[tauri::command]
async fn desktop_bootstrap(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
) -> Result<DesktopBootstrapState, String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();

    if mode != RunMode::Local {
        sync_active_session_from_storage(&app, &state)?;
        let _ = ensure_active_session_if_needed(&app, &state).await;
    }
    restart_monitor(&app);
    bootstrap_state(&app, &state)
}

#[tauri::command]
async fn desktop_save_profile(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
    profile: SaveProfileInput,
) -> Result<DesktopBootstrapState, String> {
    let mut persisted = state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())?
        .clone();

    let normalized_url = normalize_base_url(&profile.base_url);
    if normalized_url.is_empty() || profile.name.trim().is_empty() {
        return Err(localized_runtime_message(&system_language_tag(), "profile_required").to_string());
    }

    let profile_id = profile.id.unwrap_or_else(|| Uuid::new_v4().to_string());
    let mut updated = false;
    for item in &mut persisted.profiles {
        if item.id == profile_id {
            item.name = profile.name.trim().to_string();
            item.base_url = normalized_url.clone();
            item.allow_self_signed = profile.allow_self_signed;
            item.note = profile
                .note
                .clone()
                .filter(|value| !value.trim().is_empty());
            updated = true;
            break;
        }
    }

    if !updated {
        let should_activate = persisted.profiles.is_empty();
        persisted.profiles.push(DesktopProfile {
            id: profile_id.clone(),
            name: profile.name.trim().to_string(),
            base_url: normalized_url,
            allow_self_signed: profile.allow_self_signed,
            trusted_fingerprint: None,
            trusted_issuer: Some(if profile.allow_self_signed {
                "Local override enabled".to_string()
            } else {
                "System certificate trust".to_string()
            }),
            active: should_activate,
            last_connected_at: None,
            note: profile
                .note
                .clone()
                .filter(|value| !value.trim().is_empty()),
        });
    }

    if !persisted.profiles.iter().any(|item| item.active) {
        if let Some(first) = persisted.profiles.first_mut() {
            first.active = true;
        }
    }

    persist_state_to_disk(&app, &persisted)?;
    *state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())? = persisted;
    sync_active_session_from_storage(&app, &state)?;
    reset_monitor_snapshot(&state)?;
    restart_monitor(&app);
    bootstrap_state(&app, &state)
}

#[tauri::command]
async fn desktop_delete_profile(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
    profile_id: String,
) -> Result<DesktopBootstrapState, String> {
    let mut persisted = state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())?
        .clone();
    let removed_active = persisted
        .profiles
        .iter()
        .any(|item| item.id == profile_id && item.active);
    persisted.profiles.retain(|item| item.id != profile_id);

    if persisted.profiles.is_empty() {
        persisted = DesktopPersistedState::default();
    } else if removed_active {
        for item in &mut persisted.profiles {
            item.active = false;
        }
        if let Some(first) = persisted.profiles.first_mut() {
            first.active = true;
        }
    }

    let _ = delete_session_for_profile(&app, &profile_id);
    persist_state_to_disk(&app, &persisted)?;
    *state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())? = persisted;
    sync_active_session_from_storage(&app, &state)?;
    reset_monitor_snapshot(&state)?;
    restart_monitor(&app);
    bootstrap_state(&app, &state)
}

#[tauri::command]
async fn desktop_set_active_profile(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
    profile_id: String,
) -> Result<DesktopBootstrapState, String> {
    let mut persisted = state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())?
        .clone();
    let mut found = false;
    for item in &mut persisted.profiles {
        item.active = item.id == profile_id;
        if item.active {
            found = true;
        }
    }
    if !found {
        return Err(localized_runtime_message(&system_language_tag(), "profile_not_found").to_string());
    }

    persist_state_to_disk(&app, &persisted)?;
    *state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())? = persisted;
    sync_active_session_from_storage(&app, &state)?;
    reset_monitor_snapshot(&state)?;
    restart_monitor(&app);
    bootstrap_state(&app, &state)
}

#[tauri::command]
async fn desktop_probe_backend(
    _app: AppHandle,
    state: State<'_, DesktopAppState>,
    profile_id: String,
) -> Result<DesktopBackendProbe, String> {
    let persisted = state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())?
        .clone();
    let profile = persisted
        .profiles
        .into_iter()
        .find(|item| item.id == profile_id)
        .ok_or_else(|| localized_runtime_message(&system_language_tag(), "profile_not_found").to_string())?;

    let client = build_client(&profile)?;
    let url = format!("{}/api/v1/system/info", profile.base_url);
    let response = client.get(url).send().await;
    match response {
        Ok(resp) => {
            let payload = read_json_response::<SystemInfoState>(resp).await?;
            Ok(DesktopBackendProbe {
                reachable: true,
                allow_self_signed: profile.allow_self_signed,
                certificate_status: if profile.allow_self_signed {
                    "local_override".to_string()
                } else {
                    "verified".to_string()
                },
                message: localized_probe_success_message(&system_language_tag(), &profile.base_url),
                system_info: Some(payload),
            })
        }
        Err(err) => Ok(DesktopBackendProbe {
            reachable: false,
            allow_self_signed: profile.allow_self_signed,
            certificate_status: if profile.allow_self_signed {
                "local_override".to_string()
            } else {
                "untrusted".to_string()
            },
            message: err.to_string(),
            system_info: None,
        }),
    }
}

#[tauri::command]
async fn desktop_start_browser_login(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
    profile_id: String,
) -> Result<DesktopAuthStart, String> {
    let persisted = state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())?
        .clone();
    let profile = persisted
        .profiles
        .into_iter()
        .find(|item| item.id == profile_id)
        .ok_or_else(|| localized_runtime_message(&system_language_tag(), "profile_not_found").to_string())?;

    let listener = TcpListener::bind("127.0.0.1:0").map_err(|err| err.to_string())?;
    let local_addr = listener.local_addr().map_err(|err| err.to_string())?;
    let callback_url = format!("http://127.0.0.1:{}/callback", local_addr.port());
    let flow_state = Uuid::new_v4().to_string();

    {
        let mut auth_flow = state
            .auth_flow
            .lock()
            .map_err(|_| "auth flow state poisoned".to_string())?;
        *auth_flow = Some(AuthFlow {
            profile_id: profile.id.clone(),
            state: flow_state.clone(),
        });
    }

    let app_handle = app.clone();
    let flow_state_for_callback = flow_state.clone();
    std::thread::spawn(move || {
        let accept = listener.accept();
        let callback_payload = match accept {
            Ok((mut stream, _)) => {
                let mut buffer = [0u8; 4096];
                let read = stream.read(&mut buffer).unwrap_or(0);
                let request = String::from_utf8_lossy(&buffer[..read]);
                let request_line = request.lines().next().unwrap_or_default();
                let path = request_line.split_whitespace().nth(1).unwrap_or("/");
                let url = Url::parse(&format!("http://localhost{}", path));

                let payload = match url {
                    Ok(parsed) => {
                        let mut code = None;
                        let mut state_value = None;
                        let mut error = None;
                        for (key, value) in parsed.query_pairs() {
                            match key.as_ref() {
                                "code" => code = Some(value.to_string()),
                                "state" => state_value = Some(value.to_string()),
                                "error" => error = Some(value.to_string()),
                                _ => {}
                            }
                        }
                        DesktopAuthCallbackPayload {
                            state: state_value.unwrap_or_default(),
                            code,
                            error,
                        }
                    }
                    Err(err) => DesktopAuthCallbackPayload {
                        state: String::new(),
                        code: None,
                        error: Some(err.to_string()),
                    },
                };

                let language_tag = system_language_tag();
                let body = if payload.error.is_some() {
                    if is_chinese_language(&language_tag) {
                        format!(
                            "<h1>{} 登录失败</h1><p>请返回桌面端重新尝试；如果页面地址栏里已经出现了回调链接，也可以复制完整地址后粘贴回桌面端。</p>",
                            localized_brand_name(&language_tag)
                        )
                    } else {
                        format!(
                            "<h1>{} login failed</h1><p>Return to the desktop app and try again. If the address bar already contains a callback URL, copy the full address and paste it back into the desktop app.</p>",
                            localized_brand_name(&language_tag)
                        )
                    }
                } else {
                    if is_chinese_language(&language_tag) {
                        format!(
                            "<h1>{} 已授权</h1><p>请复制地址栏中的完整回调链接，然后粘贴回桌面端完成登录。如果桌面端已经自动识别，也可以再关闭这个浏览器标签页。</p>",
                            localized_brand_name(&language_tag)
                        )
                    } else {
                        format!(
                            "<h1>{} approved</h1><p>Copy the full callback URL from your browser address bar and paste it back into {} to finish sign-in. If the desktop app already picked it up automatically, you can close this tab.</p>",
                            localized_brand_name(&language_tag),
                            localized_app_name(&language_tag)
                        )
                    }
                };
                let response = format!(
                    "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\n\r\n{}",
                    body.len(),
                    body
                );
                let _ = stream.write_all(response.as_bytes());
                payload
            }
            Err(err) => DesktopAuthCallbackPayload {
                state: flow_state_for_callback.clone(),
                code: None,
                error: Some(err.to_string()),
            },
        };

        let _ = app_handle.emit("desktop://auth-callback", callback_payload);
    });

    let mut auth_url = Url::parse(&format!("{}/api/v1/auth/desktop/start", profile.base_url))
        .map_err(|err| err.to_string())?;
    let language_tag = system_language_tag();
    auth_url
        .query_pairs_mut()
        .append_pair("redirect_uri", &callback_url)
        .append_pair("state", &flow_state)
        .append_pair("client_name", localized_app_name(&language_tag));

    let result = DesktopAuthStart {
        auth_url: auth_url.to_string(),
        state: flow_state,
        callback_url,
    };
    log_info!(
        "desktop auth start created for profile={} callback_url={}",
        profile.id, result.callback_url
    );
    Ok(result)
}

#[tauri::command]
async fn desktop_exchange_browser_login(
    app: AppHandle,
    runtime: State<'_, DesktopAppState>,
    code: String,
    state: String,
) -> Result<DesktopAuthExchangeResponse, String> {
    log_info!("desktop auth exchange requested state={}", state);
    let auth_flow = runtime
        .auth_flow
        .lock()
        .map_err(|_| "auth flow state poisoned".to_string())?
        .clone();
    let flow = auth_flow.ok_or_else(|| "No desktop auth flow is currently active.".to_string())?;
    if flow.state != state {
        return Err("Desktop auth callback state mismatch.".to_string());
    }

    let persisted = runtime
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())?
        .clone();
    let profile = persisted
        .profiles
        .into_iter()
        .find(|item| item.id == flow.profile_id)
        .ok_or_else(|| "The desktop auth profile no longer exists.".to_string())?;
    let client = build_client(&profile)?;
    let response: DesktopAuthExchangeResponse = send_json_request(
        &client,
        Method::POST,
        &format!("{}/api/v1/auth/desktop/exchange", profile.base_url),
        None,
        Some(json!(AuthExchangeRequestBody { code: &code })),
    )
    .await?;

    let secrets = DesktopSessionSecrets {
        profile_id: profile.id.clone(),
        username: response.username.clone(),
        access_token: response.access_token.clone(),
        refresh_token: response.refresh_token.clone(),
        expires_at: response.expires_at.clone(),
        refresh_expires_at: response.refresh_expires_at.clone(),
    };

    save_session_for_profile(&app, &secrets)?;
    *runtime
        .session
        .lock()
        .map_err(|_| "session state poisoned".to_string())? = Some(secrets);
    *runtime
        .auth_flow
        .lock()
        .map_err(|_| "auth flow state poisoned".to_string())? = None;
    update_connection(
        &app,
        &runtime,
        DesktopConnectionSnapshot {
            state: "connected".to_string(),
            message: localized_connected_message(&system_language_tag(), &profile.name),
            last_changed_at: Some(now_rfc3339()),
        },
    )?;
    restart_monitor(&app);
    log_info!(
        "desktop auth exchange succeeded profile={} username={}",
        profile.id, response.username
    );
    Ok(response)
}

#[tauri::command]
async fn desktop_open_external_url(url: String) -> Result<(), String> {
    open_external_url(&url)
}

#[tauri::command]
async fn desktop_logout(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
) -> Result<DesktopBootstrapState, String> {
    let active_profile = current_active_profile(&state)?;
    let current_session = state
        .session
        .lock()
        .map_err(|_| "session state poisoned".to_string())?
        .clone();

    if let (Some(profile), Some(session)) = (active_profile, current_session.clone()) {
        let client = build_client(&profile)?;
        let _ = send_unit_request(
            &client,
            Method::POST,
            &format!("{}/api/v1/auth/desktop/logout", profile.base_url),
            Some(&session.access_token),
            None,
        )
        .await;
    }

    if let Some(session) = current_session {
        let _ = delete_session_for_profile(&app, &session.profile_id);
    }
    *state
        .session
        .lock()
        .map_err(|_| "session state poisoned".to_string())? = None;
    reset_monitor_snapshot(&state)?;
    update_connection(
        &app,
        &state,
        DesktopConnectionSnapshot {
            state: "degraded".to_string(),
            message: localized_runtime_message(&system_language_tag(), "signed_out").to_string(),
            last_changed_at: Some(now_rfc3339()),
        },
    )?;
    restart_monitor(&app);
    bootstrap_state(&app, &state)
}

#[tauri::command]
async fn desktop_fetch_system_info(
    _app: AppHandle,
    state: State<'_, DesktopAppState>,
) -> Result<SystemInfoState, String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();
    if mode == RunMode::Local {
        let local_store = state
            .local_store
            .lock()
            .map_err(|_| "local_store state poisoned".to_string())?;
        let store = local_store.as_ref().ok_or("Local store not initialized")?;
        let info = store.get_system_info().map_err(|e| e.to_string())?;
        return Ok(SystemInfoState {
            service: info.service,
            app_env: info.app_env,
            local_base_url: info.local_base_url,
            public_base_url: info.public_base_url,
            database_ready: info.database_ready,
            user_count: info.user_count,
            time: info.time,
        });
    }
    let profile =
        current_active_profile(&state)?
            .ok_or_else(|| localized_runtime_message(&system_language_tag(), "no_active_backend").to_string())?;
    let client = build_client(&profile)?;
    send_json_request(
        &client,
        Method::GET,
        &format!("{}/api/v1/system/info", profile.base_url),
        None,
        None,
    )
    .await
}

#[tauri::command]
async fn desktop_fetch_devices(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
) -> Result<DevicesResponse, String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();
    if mode == RunMode::Local {
        let local_store = state
            .local_store
            .lock()
            .map_err(|_| "local_store state poisoned".to_string())?;
        let store = local_store.as_ref().ok_or("Local store not initialized")?;
        let devices = store.list_devices().map_err(|e| e.to_string())?;
        return Ok(DevicesResponse {
            devices: devices.into_iter().map(DeviceItem::from).collect(),
        });
    }
    let (profile, session) = ensure_active_session_if_needed(&app, &state).await?;
    let client = build_client(&profile)?;
    send_json_request(
        &client,
        Method::GET,
        &format!("{}/api/v1/devices", profile.base_url),
        Some(&session.access_token),
        None,
    )
    .await
}

#[tauri::command]
async fn desktop_create_bind_code(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
) -> Result<BindCodeResponse, String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();
    if mode == RunMode::Local {
        let local_store = state
            .local_store
            .lock()
            .map_err(|_| "local_store state poisoned".to_string())?;
        let store = local_store.as_ref().ok_or("Local store not initialized")?;
        let bind_code = store.create_bind_code().map_err(|e| e.to_string())?;
        return Ok(BindCodeResponse {
            code: bind_code.code,
            expires_at: bind_code.expires_at,
        });
    }
    let (profile, session) = ensure_active_session_if_needed(&app, &state).await?;
    let client = build_client(&profile)?;
    send_json_request(
        &client,
        Method::POST,
        &format!("{}/api/v1/devices/bind-codes", profile.base_url),
        Some(&session.access_token),
        None,
    )
    .await
}

#[tauri::command]
async fn desktop_patch_device(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
    device_id: i64,
    payload: PatchDeviceInput,
) -> Result<Value, String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();
    if mode == RunMode::Local {
        let local_store = state
            .local_store
            .lock()
            .map_err(|_| "local_store state poisoned".to_string())?;
        let store = local_store.as_ref().ok_or("Local store not initialized")?;
        let response = store
            .patch_device(device_id, payload.display_name.as_deref(), payload.enabled)
            .map_err(|e| e.to_string())?;
        emit_realtime_event(
            &app,
            realtime_event(
                "device.updated",
                Some(json!({
                    "deviceId": device_id
                })),
            ),
        );
        return Ok(response);
    }
    let (profile, session) = ensure_active_session_if_needed(&app, &state).await?;
    let client = build_client(&profile)?;
    let response: Value = send_json_request(
        &client,
        Method::PATCH,
        &format!("{}/api/v1/devices/{}", profile.base_url, device_id),
        Some(&session.access_token),
        Some(json!(payload)),
    )
    .await?;
    let devices: DevicesResponse = send_json_request(
        &client,
        Method::GET,
        &format!("{}/api/v1/devices", profile.base_url),
        Some(&session.access_token),
        None,
    )
    .await?;
    update_monitor_snapshot(&state, |snapshot| {
        snapshot.devices = devices
            .devices
            .iter()
            .map(|device| (device.id, monitor_device_snapshot(device)))
            .collect();
    })?;
    emit_realtime_event(
        &app,
        realtime_event(
            "device.updated",
            Some(json!({
                "deviceId": device_id
            })),
        ),
    );
    Ok(response)
}

#[tauri::command]
async fn desktop_revoke_device(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
    device_id: i64,
) -> Result<Value, String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();
    if mode == RunMode::Local {
        let local_store = state
            .local_store
            .lock()
            .map_err(|_| "local_store state poisoned".to_string())?;
        let store = local_store.as_ref().ok_or("Local store not initialized")?;
        let response = store.revoke_device(device_id).map_err(|e| e.to_string())?;
        emit_realtime_event(
            &app,
            realtime_event(
                "device.revoked",
                Some(json!({
                    "deviceId": device_id
                })),
            ),
        );
        return Ok(response);
    }
    let (profile, session) = ensure_active_session_if_needed(&app, &state).await?;
    let client = build_client(&profile)?;
    let response: Value = send_json_request(
        &client,
        Method::POST,
        &format!("{}/api/v1/devices/{}/revoke", profile.base_url, device_id),
        Some(&session.access_token),
        None,
    )
    .await?;
    let devices: DevicesResponse = send_json_request(
        &client,
        Method::GET,
        &format!("{}/api/v1/devices", profile.base_url),
        Some(&session.access_token),
        None,
    )
    .await?;
    update_monitor_snapshot(&state, |snapshot| {
        snapshot.devices = devices
            .devices
            .iter()
            .map(|device| (device.id, monitor_device_snapshot(device)))
            .collect();
    })?;
    emit_realtime_event(
        &app,
        realtime_event(
            "device.revoked",
            Some(json!({
                "deviceId": device_id
            })),
        ),
    );
    Ok(response)
}

#[tauri::command]
async fn desktop_fetch_config_snapshot(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
) -> Result<ConfigSnapshotState, String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();
    if mode == RunMode::Local {
        let local_store = state
            .local_store
            .lock()
            .map_err(|_| "local_store state poisoned".to_string())?;
        let store = local_store.as_ref().ok_or("Local store not initialized")?;
        let snapshot = store.get_config_snapshot().map_err(|e| e.to_string())?;
        return Ok(ConfigSnapshotState {
            revision: snapshot.as_ref().map(|s| s.revision).unwrap_or(0),
            snapshot: snapshot.map(|s| s.snapshot).unwrap_or(serde_json::json!({})),
        });
    }
    let (profile, session) = ensure_active_session_if_needed(&app, &state).await?;
    let client = build_client(&profile)?;
    send_json_request(
        &client,
        Method::GET,
        &format!("{}/api/v1/config/snapshot", profile.base_url),
        Some(&session.access_token),
        None,
    )
    .await
}

#[tauri::command]
async fn desktop_put_config_snapshot(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
    base_revision: i64,
    snapshot: Value,
) -> Result<ConfigSnapshotState, String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();
    if mode == RunMode::Local {
        let local_store = state
            .local_store
            .lock()
            .map_err(|_| "local_store state poisoned".to_string())?;
        let store = local_store.as_ref().ok_or("Local store not initialized")?;
        let result = store.put_config_snapshot(base_revision, snapshot).map_err(|e| e.to_string())?;
        update_monitor_snapshot(&state, |monitor| {
            monitor.config_revision = Some(result.revision);
        })?;
        emit_realtime_event(
            &app,
            realtime_event(
                "config.updated",
                Some(json!({
                    "revision": result.revision
                })),
            ),
        );
        return Ok(ConfigSnapshotState {
            revision: result.revision,
            snapshot: result.snapshot,
        });
    }
    let (profile, session) = ensure_active_session_if_needed(&app, &state).await?;
    let client = build_client(&profile)?;
    let response: ConfigSnapshotState = send_json_request(
        &client,
        Method::PUT,
        &format!("{}/api/v1/config/snapshot", profile.base_url),
        Some(&session.access_token),
        Some(json!(ConfigSnapshotPutBody {
            base_revision,
            snapshot,
        })),
    )
    .await?;
    update_monitor_snapshot(&state, |monitor| {
        monitor.config_revision = Some(response.revision);
    })?;
    emit_realtime_event(
        &app,
        realtime_event(
            "config.updated",
            Some(json!({
                "revision": response.revision
            })),
        ),
    );
    Ok(response)
}

#[tauri::command]
async fn desktop_fetch_config_audit_logs(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
    limit: i32,
    offset: i32,
) -> Result<ConfigAuditLogsResponse, String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();
    if mode == RunMode::Local {
        let local_store = state
            .local_store
            .lock()
            .map_err(|_| "local_store state poisoned".to_string())?;
        let store = local_store.as_ref().ok_or("Local store not initialized")?;
        let result = store.list_config_audit_logs(limit, offset).map_err(|e| e.to_string())?;
        return Ok(ConfigAuditLogsResponse {
            logs: result.items.into_iter().map(ConfigAuditLogItem::from).collect(),
            limit: result.limit,
            offset: result.offset,
        });
    }
    let (profile, session) = ensure_active_session_if_needed(&app, &state).await?;
    let client = build_client(&profile)?;
    send_json_request(
        &client,
        Method::GET,
        &format!(
            "{}/api/v1/config/audit?limit={}&offset={}",
            profile.base_url, limit, offset
        ),
        Some(&session.access_token),
        None,
    )
    .await
}

#[tauri::command]
async fn desktop_fetch_records(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
    limit: i32,
    device_id: Option<i64>,
) -> Result<RecordsResponse, String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();
    if mode == RunMode::Local {
        let local_store = state
            .local_store
            .lock()
            .map_err(|_| "local_store state poisoned".to_string())?;
        let store = local_store.as_ref().ok_or("Local store not initialized")?;
        let result = store.list_records(limit, device_id).map_err(|e| e.to_string())?;
        return Ok(RecordsResponse {
            records: result.items.into_iter().map(RecordItem::from).collect(),
            limit: result.limit,
            offset: result.offset,
        });
    }
    let (profile, session) = ensure_active_session_if_needed(&app, &state).await?;
    let mut url = format!("{}/api/v1/records?limit={}", profile.base_url, limit);
    if let Some(device_id) = device_id {
        url.push_str(&format!("&device_id={device_id}"));
    }
    let client = build_client(&profile)?;
    send_json_request(
        &client,
        Method::GET,
        &url,
        Some(&session.access_token),
        None,
    )
    .await
}

#[tauri::command]
async fn desktop_fetch_record(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
    record_id: i64,
) -> Result<RecordItem, String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();
    if mode == RunMode::Local {
        let local_store = state
            .local_store
            .lock()
            .map_err(|_| "local_store state poisoned".to_string())?;
        let store = local_store.as_ref().ok_or("Local store not initialized")?;
        let record = store.get_record(record_id).map_err(|e| e.to_string())?;
        return Ok(RecordItem::from(record));
    }
    let (profile, session) = ensure_active_session_if_needed(&app, &state).await?;
    let client = build_client(&profile)?;
    send_json_request(
        &client,
        Method::GET,
        &format!("{}/api/v1/records/{}", profile.base_url, record_id),
        Some(&session.access_token),
        None,
    )
    .await
}

#[tauri::command]
async fn desktop_export_diagnostics(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
) -> Result<DesktopDiagnosticsExport, String> {
    let persisted = state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())?
        .clone();
    let session = state
        .session
        .lock()
        .map_err(|_| "session state poisoned".to_string())?
        .clone();
    let connection = state
        .connection
        .lock()
        .map_err(|_| "connection state poisoned".to_string())?
        .clone();

    let app_dir = ensure_directory(app.path().app_data_dir().map_err(|err| err.to_string())?)?;
    let diagnostics_dir = ensure_directory(app_dir.join("diagnostics"))?;
    let created_at = now_rfc3339();
    let file_path = diagnostics_dir.join(format!(
        "desktop-diagnostics-{}.json",
        Utc::now().timestamp()
    ));
    let payload = json!({
        "createdAt": created_at,
        "profiles": persisted.profiles,
        "notifications": persisted.notifications,
        "connection": connection,
        "session": session.as_ref().map(|item| {
            json!({
                "profileId": item.profile_id,
                "username": item.username,
                "expiresAt": item.expires_at,
                "refreshExpiresAt": item.refresh_expires_at
            })
        })
    });
    let serialized = serde_json::to_string_pretty(&payload).map_err(|err| err.to_string())?;
    fs::write(&file_path, serialized).map_err(|err| err.to_string())?;

    Ok(DesktopDiagnosticsExport {
        path: file_path.to_string_lossy().into_owned(),
        created_at,
    })
}

#[tauri::command]
async fn desktop_update_notifications(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
    preferences: DesktopNotificationPreferences,
) -> Result<DesktopBootstrapState, String> {
    let mut persisted = state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())?
        .clone();
    persisted.notifications = preferences;
    persist_state_to_disk(&app, &persisted)?;
    *state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())? = persisted;
    bootstrap_state(&app, &state)
}

#[tauri::command]
async fn desktop_send_test_notification(app: AppHandle) -> Result<(), String> {
    let language_tag = system_language_tag();
    app.notification()
        .builder()
        .title(localized_app_name(&language_tag))
        .body(if is_chinese_language(&language_tag) {
            "桌面通知工作正常。"
        } else {
            "Desktop notifications are working."
        })
        .show()
        .map_err(|err| err.to_string())
}

#[tauri::command]
async fn desktop_get_run_mode(
    state: State<'_, DesktopAppState>,
) -> Result<RunMode, String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();
    Ok(mode)
}

#[tauri::command]
async fn desktop_switch_run_mode(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
    mode: RunMode,
) -> Result<DesktopBootstrapState, String> {
    {
        let mut run_mode = state
            .run_mode
            .lock()
            .map_err(|_| "run_mode state poisoned".to_string())?;
        *run_mode = mode.clone();
    }

    // Persist run mode to disk
    {
        let mut persisted = state
            .persisted
            .lock()
            .map_err(|_| "persisted state poisoned".to_string())?;
        persisted.run_mode = mode.clone();
        let _ = persist_state_to_disk(&app, &persisted);
    }

    if mode == RunMode::Local || mode == RunMode::Hybrid {
        let mut local_store = state
            .local_store
            .lock()
            .map_err(|_| "local_store state poisoned".to_string())?;
        if local_store.is_none() {
            let app_dir = ensure_directory(app.path().app_data_dir().map_err(|err| err.to_string())?)?;
            let db_path = app_dir.join("local-data.db");
            let store = SqliteStore::open(&db_path).map_err(|e| e.to_string())?;
            *local_store = Some(store);
        }
    }

    // Auto-pull from remote when switching to Local/Hybrid (if session exists)
    if mode == RunMode::Local || mode == RunMode::Hybrid {
        let profile = current_active_profile(&state)?;
        let session = state
            .session
            .lock()
            .map_err(|_| "session state poisoned".to_string())?
            .clone();

        if let (Some(profile), Some(session)) = (profile, session) {
            if let Ok(mut remote) = RemoteStore::new(&profile.base_url, profile.allow_self_signed) {
                remote.set_access_token(Some(session.access_token));
                let local_store_guard = state
                    .local_store
                    .lock()
                    .map_err(|_| "local_store state poisoned".to_string())?;
                if let Some(local_store) = local_store_guard.as_ref() {
                    match sync::sync_initial_pull(local_store as &dyn Store, &remote) {
                        Ok(report) => {
                            log_info!(
                                "mode switch initial pull: config={:?} devices={} records={}",
                                report.config, report.devices_synced, report.records_synced
                            );
                        }
                        Err(e) => {
                            log_warn!("mode switch initial pull failed: {}", e);
                        }
                    }
                }
            }
        }
    }

    // Set connection state quietly (no notification on mode switch)
    {
        let mut connection = state
            .connection
            .lock()
            .map_err(|_| "connection state poisoned".to_string())?;
        let language_tag = system_language_tag();
        *connection = DesktopConnectionSnapshot {
            state: match mode {
                RunMode::Local => "local".to_string(),
                RunMode::Remote => "connecting".to_string(),
                RunMode::Hybrid => "hybrid".to_string(),
            },
            message: match mode {
                RunMode::Local => String::new(),
                RunMode::Remote => localized_runtime_text(&language_tag, "initializing").to_string(),
                RunMode::Hybrid => String::new(),
            },
            last_changed_at: Some(now_rfc3339()),
        };
        let _ = app.emit("desktop://connection", connection.clone());
    }
    restart_monitor(&app);
    bootstrap_state(&app, &state)
}

#[tauri::command]
async fn desktop_sync(
    app: AppHandle,
    state: State<'_, DesktopAppState>,
    direction: String,
) -> Result<sync::SyncReport, String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();

    if mode != RunMode::Hybrid {
        return Err("Sync is only available in hybrid mode".to_string());
    }

    let local_store_guard = state
        .local_store
        .lock()
        .map_err(|_| "local_store state poisoned".to_string())?;
    let local_store = local_store_guard.as_ref().ok_or("Local store not initialized")?;

    let profile = current_active_profile(&state)?
        .ok_or_else(|| "No active backend profile for sync".to_string())?;
    let session = state
        .session
        .lock()
        .map_err(|_| "session state poisoned".to_string())?
        .clone();
    let session = session.ok_or_else(|| "Login required for sync".to_string())?;

    let mut remote = RemoteStore::new(&profile.base_url, profile.allow_self_signed)
        .map_err(|e| e.to_string())?;
    remote.set_access_token(Some(session.access_token));

    let report = match direction.as_str() {
        "pull" => sync::sync_pull(local_store as &dyn Store, &remote),
        "push" => sync::sync_push(local_store as &dyn Store, &remote),
        _ => return Err("Invalid sync direction. Use 'pull' or 'push'.".to_string()),
    };

    match report {
        Ok(report) => {
            emit_realtime_event(
                &app,
                realtime_event(
                    "sync.completed",
                    Some(json!({
                        "direction": direction,
                        "config": report.config,
                        "devicesSynced": report.devices_synced,
                        "recordsSynced": report.records_synced,
                    })),
                ),
            );
            Ok(report)
        }
        Err(e) => Err(e.to_string()),
    }
}

fn bootstrap_state(
    app: &AppHandle,
    state: &DesktopAppState,
) -> Result<DesktopBootstrapState, String> {
    let persisted = state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())?
        .clone();
    let session = state
        .session
        .lock()
        .map_err(|_| "session state poisoned".to_string())?
        .clone();
    let connection = state
        .connection
        .lock()
        .map_err(|_| "connection state poisoned".to_string())?
        .clone();

    Ok(DesktopBootstrapState {
        app_version: app.package_info().version.to_string(),
        platform: std::env::consts::OS.to_string(),
        language_tag: system_language_tag(),
        profiles: persisted.profiles,
        session: DesktopSessionState {
            authenticated: session.is_some(),
            username: session.as_ref().map(|item| item.username.clone()),
            expires_at: session.as_ref().map(|item| item.expires_at.clone()),
            refresh_expires_at: session.as_ref().map(|item| item.refresh_expires_at.clone()),
        },
        notifications: persisted.notifications,
        connection,
    })
}

fn normalize_base_url(value: &str) -> String {
    value.trim().trim_end_matches('/').to_string()
}

fn now_rfc3339() -> String {
    Utc::now().to_rfc3339()
}

fn sync_active_session_from_storage(
    app: &AppHandle,
    state: &DesktopAppState,
) -> Result<(), String> {
    let persisted = state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())?
        .clone();
    let active_profile_id = active_profile(&persisted).map(|item| item.id);
    let current_session = state
        .session
        .lock()
        .map_err(|_| "session state poisoned".to_string())?
        .clone();
    let next_session = match active_profile_id {
        Some(profile_id) => match load_session_for_profile(app, &profile_id) {
            Ok(Some(session)) => Some(session),
            Ok(None) => {
                if current_session
                    .as_ref()
                    .map(|item| item.profile_id.as_str())
                    == Some(profile_id.as_str())
                {
                    log_warn!(
                        "desktop session missing from keyring, preserving in-memory session for profile={}",
                        profile_id
                    );
                    current_session
                } else {
                    None
                }
            }
            Err(err) => {
                if current_session
                    .as_ref()
                    .map(|item| item.profile_id.as_str())
                    == Some(profile_id.as_str())
                {
                    log_error!(
                        "desktop session keyring reload failed for profile={}, preserving in-memory session: {}",
                        profile_id, err
                    );
                    current_session
                } else {
                    return Err(err);
                }
            }
        },
        None => None,
    };
    *state
        .session
        .lock()
        .map_err(|_| "session state poisoned".to_string())? = next_session;
    let _ = app.emit(
        "desktop://connection",
        state
            .connection
            .lock()
            .map_err(|_| "connection state poisoned".to_string())?
            .clone(),
    );
    Ok(())
}

fn parse_timestamp(value: &str) -> Result<DateTime<Utc>, String> {
    DateTime::parse_from_rfc3339(value)
        .map(|item| item.with_timezone(&Utc))
        .map_err(|err| err.to_string())
}

fn build_client(profile: &DesktopProfile) -> Result<Client, String> {
    let mut builder = Client::builder().timeout(Duration::from_secs(15));
    if profile.allow_self_signed {
        builder = builder.danger_accept_invalid_certs(true);
    }
    builder.build().map_err(|err| err.to_string())
}

async fn ensure_active_session_if_needed(
    app: &AppHandle,
    state: &DesktopAppState,
) -> Result<(DesktopProfile, DesktopSessionSecrets), String> {
    sync_active_session_from_storage(app, state)?;
    let profile =
        current_active_profile(state)?
            .ok_or_else(|| localized_runtime_message(&system_language_tag(), "no_active_backend").to_string())?;
    let current_session = state
        .session
        .lock()
        .map_err(|_| "session state poisoned".to_string())?
        .clone()
        .ok_or_else(|| localized_runtime_message(&system_language_tag(), "login_required").to_string())?;

    if parse_timestamp(&current_session.refresh_expires_at)? <= Utc::now() {
        let _ = delete_session_for_profile(app, &current_session.profile_id);
        *state
            .session
            .lock()
            .map_err(|_| "session state poisoned".to_string())? = None;
        return Err("Desktop session expired. Please sign in again.".to_string());
    }

    if parse_timestamp(&current_session.expires_at)? <= Utc::now() {
        let refreshed = refresh_session(app, state, &profile, &current_session).await?;
        return Ok((profile, refreshed));
    }

    Ok((profile, current_session))
}

async fn refresh_session(
    _app: &AppHandle,
    state: &DesktopAppState,
    profile: &DesktopProfile,
    current_session: &DesktopSessionSecrets,
) -> Result<DesktopSessionSecrets, String> {
    let client = build_client(profile)?;
    let response: DesktopAuthExchangeResponse = send_json_request(
        &client,
        Method::POST,
        &format!("{}/api/v1/auth/desktop/refresh", profile.base_url),
        None,
        Some(json!(RefreshRequestBody {
            refresh_token: &current_session.refresh_token,
        })),
    )
    .await?;

    let refreshed = DesktopSessionSecrets {
        profile_id: profile.id.clone(),
        username: response.username.clone(),
        access_token: response.access_token,
        refresh_token: response.refresh_token,
        expires_at: response.expires_at,
        refresh_expires_at: response.refresh_expires_at,
    };
    save_session_for_profile(_app, &refreshed)?;
    *state
        .session
        .lock()
        .map_err(|_| "session state poisoned".to_string())? = Some(refreshed.clone());
    Ok(refreshed)
}

async fn send_json_request<T: DeserializeOwned>(
    client: &Client,
    method: Method,
    url: &str,
    bearer_token: Option<&str>,
    body: Option<Value>,
) -> Result<T, String> {
    let mut builder = client.request(method, url);
    if let Some(token) = bearer_token {
        builder = builder.bearer_auth(token);
    }
    if let Some(body) = body {
        builder = builder.json(&body);
    }
    let response = builder.send().await.map_err(|err| err.to_string())?;
    read_json_response(response).await
}

async fn send_unit_request(
    client: &Client,
    method: Method,
    url: &str,
    bearer_token: Option<&str>,
    body: Option<Value>,
) -> Result<(), String> {
    let mut builder = client.request(method, url);
    if let Some(token) = bearer_token {
        builder = builder.bearer_auth(token);
    }
    if let Some(body) = body {
        builder = builder.json(&body);
    }
    let response = builder.send().await.map_err(|err| err.to_string())?;
    if response.status().is_success() {
        return Ok(());
    }
    let status = response.status();
    let text = response.text().await.unwrap_or_default();
    Err(extract_error_message(status, &text))
}

async fn read_json_response<T: DeserializeOwned>(response: reqwest::Response) -> Result<T, String> {
    let status = response.status();
    let text = response.text().await.map_err(|err| err.to_string())?;
    if !status.is_success() {
        return Err(extract_error_message(status, &text));
    }
    serde_json::from_str(&text).map_err(|err| err.to_string())
}

fn extract_error_message(status: StatusCode, text: &str) -> String {
    if let Ok(payload) = serde_json::from_str::<ErrorPayload>(text) {
        if let Some(error) = payload.error {
            return error;
        }
    }
    if text.trim().is_empty() {
        format!("Backend request failed with status {status}.")
    } else {
        text.trim().to_string()
    }
}

fn open_external_url(url: &str) -> Result<(), String> {
    let candidates: Vec<(&str, Vec<&str>)> = if cfg!(target_os = "windows") {
        vec![("cmd", vec!["/C", "start", "", url])]
    } else if cfg!(target_os = "macos") {
        vec![("open", vec![url])]
    } else {
        vec![
            ("xdg-open", vec![url]),
            ("gio", vec!["open", url]),
            ("gnome-open", vec![url]),
        ]
    };

    let mut last_error = None;
    for (command, args) in candidates {
        match Command::new(command)
            .args(args)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
        {
            Ok(status) if status.success() => {
                log_info!("opened external url via {} -> {}", command, url);
                return Ok(());
            }
            Ok(status) => {
                last_error = Some(format!("{command}: exited with status {status}"));
            }
            Err(err) => last_error = Some(format!("{command}: {err}")),
        }
    }

    let error_message = last_error.unwrap_or_else(|| "No system browser launcher is available.".to_string());
    log_error!("failed to open external url {}: {}", url, error_message);
    Err(error_message)
}

fn realtime_event(event_type: &str, data: Option<Value>) -> RealtimeEvent {
    RealtimeEvent {
        r#type: event_type.to_string(),
        time: now_rfc3339(),
        data,
    }
}

fn emit_realtime_event(app: &AppHandle, event: RealtimeEvent) {
    let _ = app.emit("desktop://realtime", event);
}

fn summarize_monitor_snapshot(
    devices: &[DeviceItem],
    records: &[RecordItem],
    config_revision: Option<i64>,
) -> MonitorSnapshot {
    MonitorSnapshot {
        devices: devices
            .iter()
            .map(|device| (device.id, monitor_device_snapshot(device)))
            .collect(),
        latest_record: records.first().map(monitor_record_snapshot),
        config_revision,
    }
}

fn monitor_device_snapshot(device: &DeviceItem) -> MonitorDeviceSnapshot {
    MonitorDeviceSnapshot {
        display_name: if device.display_name.trim().is_empty() {
            device.device_name.clone()
        } else {
            device.display_name.clone()
        },
        enabled: device.enabled,
        revoked_at: device.revoked_at.clone(),
        last_seen_at: device.last_seen_at.clone(),
        updated_at: device.updated_at.clone(),
    }
}

fn monitor_record_snapshot(record: &RecordItem) -> MonitorRecordSnapshot {
    MonitorRecordSnapshot {
        id: record.id,
        record_type: record.record_type.clone(),
        sender: record.sender.clone(),
        sms_code: record.sms_code.clone(),
    }
}

fn diff_monitor_events(previous: &MonitorSnapshot, next: &MonitorSnapshot) -> Vec<RealtimeEvent> {
    let mut events = Vec::new();

    if !previous.devices.is_empty() {
        for (device_id, previous_device) in &previous.devices {
            match next.devices.get(device_id) {
                None => events.push(realtime_event(
                    "device.revoked",
                    Some(json!({
                        "deviceId": device_id
                    })),
                )),
                Some(next_device) => {
                    let revoked_now =
                        previous_device.revoked_at.is_none() && next_device.revoked_at.is_some();
                    if revoked_now {
                        events.push(realtime_event(
                            "device.revoked",
                            Some(json!({
                                "deviceId": device_id,
                                "displayName": next_device.display_name
                            })),
                        ));
                        continue;
                    }

                    let changed_core_state = previous_device.display_name != next_device.display_name
                        || previous_device.enabled != next_device.enabled
                        || previous_device.revoked_at != next_device.revoked_at
                        || (previous_device.updated_at != next_device.updated_at
                            && previous_device.last_seen_at == next_device.last_seen_at);
                    if changed_core_state {
                        events.push(realtime_event(
                            "device.updated",
                            Some(json!({
                                "deviceId": device_id,
                                "displayName": next_device.display_name,
                                "enabled": next_device.enabled
                            })),
                        ));
                        continue;
                    }

                    if previous_device.last_seen_at != next_device.last_seen_at {
                        events.push(realtime_event(
                            "device.heartbeat",
                            Some(json!({
                                "deviceId": device_id,
                                "displayName": next_device.display_name,
                                "lastSeenAt": next_device.last_seen_at
                            })),
                        ));
                    }
                }
            }
        }

        for (device_id, next_device) in &next.devices {
            if !previous.devices.contains_key(device_id) {
                events.push(realtime_event(
                    "device.registered",
                    Some(json!({
                        "deviceId": device_id,
                        "displayName": next_device.display_name
                    })),
                ));
            }
        }
    }

    if let (Some(previous_revision), Some(next_revision)) =
        (previous.config_revision, next.config_revision)
    {
        if previous_revision != next_revision {
            events.push(realtime_event(
                "config.updated",
                Some(json!({
                    "revision": next_revision
                })),
            ));
        }
    }

    if let (Some(previous_record), Some(next_record)) = (&previous.latest_record, &next.latest_record)
    {
        if previous_record.id != next_record.id {
            events.push(realtime_event(
                "records.ingested",
                Some(json!({
                    "recordId": next_record.id,
                    "recordType": next_record.record_type,
                    "sender": next_record.sender,
                    "smsCode": next_record.sms_code
                })),
            ));
        }
    }

    events
}

fn update_monitor_snapshot(
    state: &DesktopAppState,
    transform: impl FnOnce(&mut MonitorSnapshot),
) -> Result<(), String> {
    let mut snapshot = state
        .last_monitor
        .lock()
        .map_err(|_| "monitor state poisoned".to_string())?;
    transform(&mut snapshot);
    Ok(())
}

fn reset_monitor_snapshot(state: &DesktopAppState) -> Result<(), String> {
    update_monitor_snapshot(state, |snapshot| *snapshot = MonitorSnapshot::default())
}

pub(crate) fn restart_monitor(app: &AppHandle) {
    let next_generation = app
        .state::<DesktopAppState>()
        .monitor_generation
        .fetch_add(1, Ordering::SeqCst)
        + 1;
    let app_handle = app.clone();

    tauri::async_runtime::spawn(async move {
        loop {
            let state = app_handle.state::<DesktopAppState>();
            if state.monitor_generation.load(Ordering::SeqCst) != next_generation {
                break;
            }
            let _ = monitor_tick(&app_handle, &state).await;
            tokio::time::sleep(Duration::from_secs(20)).await;
        }
    });
}

async fn monitor_tick(app: &AppHandle, state: &DesktopAppState) -> Result<(), String> {
    let mode = state
        .run_mode
        .lock()
        .map_err(|_| "run_mode state poisoned".to_string())?
        .clone();

    log_info!("monitor_tick: mode={:?}", mode);

    if mode == RunMode::Local {
        update_connection(
            app,
            state,
            DesktopConnectionSnapshot {
                state: "local".to_string(),
                message: String::new(),
                last_changed_at: Some(now_rfc3339()),
            },
        )?;
        return Ok(());
    }

    let profile = current_active_profile(state)?;
    let Some(profile) = profile else {
        let language_tag = system_language_tag();
        update_connection(
            app,
            state,
            DesktopConnectionSnapshot {
                state: "disconnected".to_string(),
                message: localized_runtime_text(&language_tag, "no_active_profile").to_string(),
                last_changed_at: Some(now_rfc3339()),
            },
        )?;
        return Ok(());
    };

    let client = build_client(&profile)?;
    match send_json_request::<SystemInfoState>(
        &client,
        Method::GET,
        &format!("{}/api/v1/system/info", profile.base_url),
        None,
        None,
    )
    .await
    {
        Ok(system_info) => {
            {
                let mut persisted = state
                    .persisted
                    .lock()
                    .map_err(|_| "persisted state poisoned".to_string())?;
                if let Some(item) = persisted
                    .profiles
                    .iter_mut()
                    .find(|item| item.id == profile.id)
                {
                    item.last_connected_at = Some(now_rfc3339());
                }
                let _ = persist_state_to_disk(app, &persisted);
            }

            let maybe_session = state
                .session
                .lock()
                .map_err(|_| "session state poisoned".to_string())?
                .clone();
            if maybe_session.is_none() {
                let language_tag = system_language_tag();
                update_connection(
                    app,
                    state,
                    DesktopConnectionSnapshot {
                        state: "degraded".to_string(),
                        message: localized_backend_login_hint(&language_tag, &profile.name),
                        last_changed_at: Some(now_rfc3339()),
                    },
                )?;
                return Ok(());
            }

            let previous = state
                .last_monitor
                .lock()
                .map_err(|_| "monitor state poisoned".to_string())?
                .clone();
            let (_profile, session) = ensure_active_session_if_needed(app, state).await?;
            let devices: DevicesResponse = send_json_request(
                &client,
                Method::GET,
                &format!("{}/api/v1/devices", profile.base_url),
                Some(&session.access_token),
                None,
            )
            .await?;
            let records: RecordsResponse = send_json_request(
                &client,
                Method::GET,
                &format!("{}/api/v1/records?limit=1", profile.base_url),
                Some(&session.access_token),
                None,
            )
            .await?;
            let config_revision = match send_json_request::<ConfigSnapshotState>(
                &client,
                Method::GET,
                &format!("{}/api/v1/config/snapshot", profile.base_url),
                Some(&session.access_token),
                None,
            )
            .await
            {
                Ok(snapshot) => Some(snapshot.revision),
                Err(_) => previous.config_revision,
            };

            let next = summarize_monitor_snapshot(&devices.devices, &records.records, config_revision);
            let events = diff_monitor_events(&previous, &next);
            let prefs = state
                .persisted
                .lock()
                .map_err(|_| "persisted state poisoned".to_string())?
                .notifications
                .clone();

            for event in &events {
                emit_realtime_event(app, event.clone());
                match event.r#type.as_str() {
                    "device.registered" if prefs.enabled && prefs.devices => {
                        let language_tag = system_language_tag();
                        let _ = notify(
                            app,
                            localized_runtime_text(&language_tag, "device_update"),
                            localized_runtime_text(&language_tag, "device_update_body"),
                        );
                    }
                    "device.updated" if prefs.enabled && prefs.devices => {
                        let language_tag = system_language_tag();
                        let _ = notify(
                            app,
                            localized_runtime_text(&language_tag, "device_updated"),
                            localized_runtime_text(&language_tag, "device_updated_body"),
                        );
                    }
                    "device.revoked" if prefs.enabled && prefs.devices => {
                        let language_tag = system_language_tag();
                        let _ = notify(
                            app,
                            localized_runtime_text(&language_tag, "device_revoked"),
                            localized_runtime_text(&language_tag, "device_revoked_body"),
                        );
                    }
                    "records.ingested" if prefs.enabled && prefs.records => {
                        if let Some(record) = &next.latest_record {
                            let language_tag = system_language_tag();
                            let summary = localized_record_summary(
                                &language_tag,
                                &record.record_type,
                                &record.sender,
                                &record.sms_code,
                            );
                            let _ = notify(
                                app,
                                localized_runtime_text(&language_tag, "record_batch_received"),
                                &summary,
                            );
                        }
                    }
                    _ => {}
                }
            }

            *state
                .last_monitor
                .lock()
                .map_err(|_| "monitor state poisoned".to_string())? = next;
            update_connection(
                app,
                state,
                DesktopConnectionSnapshot {
                    state: "connected".to_string(),
                    message: localized_connected_summary(
                        &system_language_tag(),
                        &profile.name,
                        system_info.user_count,
                        &system_info.app_env,
                    ),
                    last_changed_at: Some(now_rfc3339()),
                },
            )?;
        }
        Err(error) => {
            update_connection(
                app,
                state,
                DesktopConnectionSnapshot {
                    state: "disconnected".to_string(),
                    message: error,
                    last_changed_at: Some(now_rfc3339()),
                },
            )?;
        }
    }

    Ok(())
}

fn update_connection(
    app: &AppHandle,
    state: &DesktopAppState,
    next: DesktopConnectionSnapshot,
) -> Result<(), String> {
    let mut connection = state
        .connection
        .lock()
        .map_err(|_| "connection state poisoned".to_string())?;
    let previous = connection.clone();
    let changed = previous.state != next.state || previous.message != next.message;
    *connection = next.clone();
    drop(connection);

    if changed {
        let _ = app.emit("desktop://connection", next.clone());
        let prefs = state
            .persisted
            .lock()
            .map_err(|_| "persisted state poisoned".to_string())?
            .notifications
            .clone();
        if prefs.enabled && prefs.connection && previous.state != next.state {
            let language_tag = system_language_tag();
            let title = if next.state == "connected" {
                localized_runtime_text(&language_tag, "backend_connected")
            } else {
                localized_runtime_text(&language_tag, "backend_state_changed")
            };
            let _ = notify(app, title, &next.message);
        }
    }

    Ok(())
}

fn notify(app: &AppHandle, title: &str, body: &str) -> Result<(), String> {
    app.notification()
        .builder()
        .title(title)
        .body(body)
        .show()
        .map_err(|err| err.to_string())
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            show_main_window(app);
        }))
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_notification::init())
        .setup(|app| {
            // 初始化日志文件
            if let Err(e) = logger::init_log_file(&app.handle()) {
                log_error!("Failed to initialize log file: {}", e);
            }
            log_info!("Xinyi Relay Desktop starting up");

            // 初始化 keyring 后端（使用 secret-service，兼容 KDE Wallet / GNOME Keyring）
            if let Err(e) = keyring::use_native_store(true) {
                log_error!("Failed to initialize keyring native store, falling back: {}", e);
                if let Err(e2) = keyring::use_named_store("sample") {
                    log_error!("Failed to initialize keyring sample store: {}", e2);
                }
            }

            let language_tag = system_language_tag();
            let persisted = load_persisted_state(&app.handle())?;
            let active_profile = active_profile(&persisted);
            let session = match active_profile.as_ref() {
                Some(profile) => load_session_for_profile(&app.handle(), &profile.id)?,
                None => None,
            };
            let initial_run_mode = persisted.run_mode.clone();
            app.manage(DesktopAppState {
                persisted: Mutex::new(persisted),
                session: Mutex::new(session),
                auth_flow: Mutex::new(None),
                connection: Mutex::new(DesktopConnectionSnapshot::default()),
                last_monitor: Mutex::new(MonitorSnapshot::default()),
                monitor_generation: AtomicU64::new(0),
                run_mode: Mutex::new(initial_run_mode.clone()),
                local_store: Mutex::new(None),
            });

            // Init local store at startup if persisted mode is Local or Hybrid
            if initial_run_mode == RunMode::Local || initial_run_mode == RunMode::Hybrid {
                let app_dir = app.path().app_data_dir().map_err(|err| err.to_string())?;
                std::fs::create_dir_all(&app_dir).ok();
                let db_path = app_dir.join("local-data.db");
                match SqliteStore::open(&db_path) {
                    Ok(store) => {
                        let state = app.state::<DesktopAppState>();
                        if let Ok(mut ls) = state.local_store.lock() {
                            *ls = Some(store);
                        }
                    }
                    Err(e) => {
                        log_error!("Failed to init local store at startup: {}", e);
                    }
                }
            }

            setup_tray(&app.handle(), &language_tag)?;
            restart_monitor(&app.handle());
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            desktop_bootstrap,
            desktop_save_profile,
            desktop_delete_profile,
            desktop_set_active_profile,
            desktop_probe_backend,
            desktop_start_browser_login,
            desktop_open_external_url,
            desktop_exchange_browser_login,
            desktop_logout,
            desktop_fetch_system_info,
            desktop_fetch_devices,
            desktop_create_bind_code,
            desktop_patch_device,
            desktop_revoke_device,
            desktop_fetch_config_snapshot,
            desktop_put_config_snapshot,
            desktop_fetch_config_audit_logs,
            desktop_fetch_records,
            desktop_fetch_record,
            desktop_export_diagnostics,
            desktop_update_notifications,
            desktop_send_test_notification,
            desktop_get_run_mode,
            desktop_switch_run_mode,
            desktop_sync
        ])
        .on_window_event(|window, event| {
            if window.label() != "main" {
                return;
            }
            if let WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running xinyi relay desktop");
}

#[cfg(test)]
mod tests {
    use super::*;

    fn monitor_device(
        display_name: &str,
        enabled: bool,
        revoked_at: Option<&str>,
        last_seen_at: Option<&str>,
        updated_at: &str,
    ) -> MonitorDeviceSnapshot {
        MonitorDeviceSnapshot {
            display_name: display_name.to_string(),
            enabled,
            revoked_at: revoked_at.map(str::to_string),
            last_seen_at: last_seen_at.map(str::to_string),
            updated_at: updated_at.to_string(),
        }
    }

    fn monitor_record(id: i64, record_type: &str, sender: &str, sms_code: &str) -> MonitorRecordSnapshot {
        MonitorRecordSnapshot {
            id,
            record_type: record_type.to_string(),
            sender: sender.to_string(),
            sms_code: sms_code.to_string(),
        }
    }

    #[test]
    fn diff_monitor_events_emits_expected_device_config_and_record_events() {
        let previous = MonitorSnapshot {
            devices: BTreeMap::from([
                (
                    1,
                    monitor_device(
                        "Alpha",
                        true,
                        None,
                        Some("2026-04-09T10:00:00Z"),
                        "2026-04-09T10:00:00Z",
                    ),
                ),
                (
                    2,
                    monitor_device(
                        "Beta",
                        true,
                        None,
                        Some("2026-04-09T10:00:00Z"),
                        "2026-04-09T10:00:00Z",
                    ),
                ),
                (
                    3,
                    monitor_device(
                        "Gamma",
                        true,
                        None,
                        Some("2026-04-09T10:00:00Z"),
                        "2026-04-09T10:00:00Z",
                    ),
                ),
            ]),
            latest_record: Some(monitor_record(100, "sms_code", "Bank", "123456")),
            config_revision: Some(5),
        };
        let next = MonitorSnapshot {
            devices: BTreeMap::from([
                (
                    1,
                    monitor_device(
                        "Alpha",
                        true,
                        None,
                        Some("2026-04-09T10:05:00Z"),
                        "2026-04-09T10:00:00Z",
                    ),
                ),
                (
                    2,
                    monitor_device(
                        "Beta Renamed",
                        false,
                        None,
                        Some("2026-04-09T10:00:00Z"),
                        "2026-04-09T10:05:00Z",
                    ),
                ),
                (
                    3,
                    monitor_device(
                        "Gamma",
                        true,
                        Some("2026-04-09T10:06:00Z"),
                        Some("2026-04-09T10:00:00Z"),
                        "2026-04-09T10:06:00Z",
                    ),
                ),
                (
                    4,
                    monitor_device(
                        "Delta",
                        true,
                        None,
                        Some("2026-04-09T10:06:00Z"),
                        "2026-04-09T10:06:00Z",
                    ),
                ),
            ]),
            latest_record: Some(monitor_record(101, "sms_plain", "Carrier", "")),
            config_revision: Some(6),
        };

        let events = diff_monitor_events(&previous, &next);
        let event_types = events
            .iter()
            .map(|event| event.r#type.as_str())
            .collect::<Vec<_>>();

        assert_eq!(
            event_types,
            vec![
                "device.heartbeat",
                "device.updated",
                "device.revoked",
                "device.registered",
                "config.updated",
                "records.ingested",
            ]
        );
    }

    #[test]
    fn diff_monitor_events_skips_initial_snapshot_noise() {
        let previous = MonitorSnapshot::default();
        let next = MonitorSnapshot {
            devices: BTreeMap::from([(
                1,
                monitor_device(
                    "Alpha",
                    true,
                    None,
                    Some("2026-04-09T10:00:00Z"),
                    "2026-04-09T10:00:00Z",
                ),
            )]),
            latest_record: Some(monitor_record(100, "sms_code", "Bank", "654321")),
            config_revision: Some(8),
        };

        let events = diff_monitor_events(&previous, &next);
        assert!(events.is_empty());
    }
}
