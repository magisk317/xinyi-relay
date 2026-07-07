use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceConfigMirror {
    pub revision: i64,
    pub snapshot: Value,
    pub updated_at: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Device {
    pub id: i64,
    pub user_id: i64,
    pub device_name: String,
    pub device_model: String,
    pub platform: String,
    pub app_version: String,
    pub display_name: String,
    pub enabled: bool,
    pub revoked_at: Option<String>,
    pub last_seen_at: Option<String>,
    pub local_addresses: Value,
    pub capabilities: Value,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Record {
    pub id: i64,
    pub device_id: i64,
    pub event_id: Option<String>,
    pub record_type: String,
    pub sender: String,
    pub body: String,
    pub sms_code: String,
    pub package_name: String,
    pub metadata: Value,
    pub msg_type: i32,
    pub call_type: i32,
    pub occurred_at: String,
    pub uploaded_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceConfigCommand {
    pub id: i64,
    pub base_revision: i64,
    pub target_revision: i64,
    pub mutation: Value,
    pub summary: String,
    pub actor_type: String,
    pub actor_id: i64,
    pub status: String,
    pub failure_reason: Option<String>,
    pub created_at: String,
    pub updated_at: String,
    pub applied_at: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceConfigAuditLog {
    pub id: i64,
    pub device_id: i64,
    pub command_id: Option<i64>,
    pub revision: i64,
    pub event_type: String,
    pub actor_type: String,
    pub actor_id: i64,
    pub summary: String,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceConfigState {
    pub device_id: i64,
    pub revision: i64,
    #[serde(rename = "mirrorContent")]
    pub snapshot: Value,
    pub pending_commands: Vec<DeviceConfigCommand>,
    pub updated_at: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BindCode {
    pub code: String,
    pub expires_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SystemInfo {
    pub service: String,
    pub app_env: String,
    pub local_base_url: String,
    pub public_base_url: String,
    pub database_ready: bool,
    pub user_count: i64,
    pub time: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Paginated<T> {
    pub items: Vec<T>,
    pub limit: i32,
    pub offset: i32,
}

#[derive(Debug, thiserror::Error)]
pub enum StoreError {
    #[error("{0}")]
    Internal(String),
    #[error("conflict: local revision {local} vs remote revision {remote}")]
    Conflict { local: i64, remote: i64 },
}

impl From<String> for StoreError {
    fn from(s: String) -> Self {
        StoreError::Internal(s)
    }
}

impl From<rusqlite::Error> for StoreError {
    fn from(e: rusqlite::Error) -> Self {
        StoreError::Internal(e.to_string())
    }
}

impl From<reqwest::Error> for StoreError {
    fn from(e: reqwest::Error) -> Self {
        StoreError::Internal(e.to_string())
    }
}

impl From<serde_json::Error> for StoreError {
    fn from(e: serde_json::Error) -> Self {
        StoreError::Internal(e.to_string())
    }
}

pub type StoreResult<T> = Result<T, StoreError>;

pub trait Store: Send {
    // Config
    fn get_device_config(&self, device_id: i64) -> StoreResult<Option<DeviceConfigState>>;
    fn upsert_device_config_mirror(
        &self,
        device_id: i64,
        revision: i64,
        snapshot: Value,
        updated_at: Option<String>,
    ) -> StoreResult<DeviceConfigState>;
    fn queue_device_config_command(
        &self,
        device_id: i64,
        base_revision: i64,
        summary: String,
        mutation: Value,
    ) -> StoreResult<DeviceConfigCommand>;
    fn list_device_config_audit_logs(
        &self,
        device_id: i64,
        limit: i32,
        offset: i32,
    ) -> StoreResult<Paginated<DeviceConfigAuditLog>>;
    fn replace_device_config_pending_commands(
        &self,
        device_id: i64,
        commands: Vec<DeviceConfigCommand>,
    ) -> StoreResult<()>;
    fn clear_device_config_pending_commands(&self, device_id: i64) -> StoreResult<()>;

    // Devices
    fn list_devices(&self) -> StoreResult<Vec<Device>>;
    fn patch_device(
        &self,
        device_id: i64,
        display_name: Option<&str>,
        enabled: Option<bool>,
    ) -> StoreResult<Value>;
    fn revoke_device(&self, device_id: i64) -> StoreResult<Value>;
    fn create_bind_code(&self) -> StoreResult<BindCode>;
    fn upsert_devices(&self, devices: Vec<Device>) -> StoreResult<()>;

    // Records
    fn list_records(&self, limit: i32, device_id: Option<i64>) -> StoreResult<Paginated<Record>>;
    fn get_record(&self, record_id: i64) -> StoreResult<Record>;
    fn upsert_records(&self, records: Vec<Record>) -> StoreResult<()>;

    // System
    fn get_system_info(&self) -> StoreResult<SystemInfo>;
}
