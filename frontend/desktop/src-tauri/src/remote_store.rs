use reqwest::StatusCode;
use reqwest::blocking::Client;
use serde::de::DeserializeOwned;
use serde_json::{Value, json};

use super::store::*;

pub struct RemoteStore {
    client: Client,
    base_url: String,
    access_token: Option<String>,
}

#[derive(Debug, serde::Deserialize)]
struct ErrorPayload {
    error: Option<String>,
}

#[derive(Debug, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
struct DevicesResponse {
    devices: Vec<Device>,
}

#[derive(Debug, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
struct RecordsResponse {
    records: Vec<Record>,
    limit: i32,
    offset: i32,
}

#[derive(Debug, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
struct DeviceConfigAuditLogsResponse {
    logs: Vec<DeviceConfigAuditLog>,
    limit: i32,
    offset: i32,
}

#[derive(Debug, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
struct BindCodeResponse {
    code: String,
    expires_at: String,
}

impl RemoteStore {
    pub fn new(base_url: &str, allow_self_signed: bool) -> StoreResult<Self> {
        let mut builder = Client::builder().timeout(std::time::Duration::from_secs(15));
        if allow_self_signed {
            builder = builder.danger_accept_invalid_certs(true);
        }
        let client = builder.build()?;
        Ok(RemoteStore {
            client,
            base_url: base_url.trim_end_matches('/').to_string(),
            access_token: None,
        })
    }

    pub fn set_access_token(&mut self, token: Option<String>) {
        self.access_token = token;
    }

    fn url(&self, path: &str) -> String {
        format!("{}{}", self.base_url, path)
    }

    fn get<T: DeserializeOwned>(&self, path: &str, auth: bool) -> StoreResult<T> {
        let mut req = self.client.get(self.url(path));
        if auth {
            if let Some(token) = &self.access_token {
                req = req.bearer_auth(token);
            }
        }
        let resp = req
            .send()
            .map_err(|e| StoreError::Internal(e.to_string()))?;
        read_response(resp)
    }

    fn post<T: DeserializeOwned>(
        &self,
        path: &str,
        body: Option<Value>,
        auth: bool,
    ) -> StoreResult<T> {
        let mut req = self.client.post(self.url(path));
        if auth {
            if let Some(token) = &self.access_token {
                req = req.bearer_auth(token);
            }
        }
        if let Some(b) = body {
            req = req.json(&b);
        }
        let resp = req
            .send()
            .map_err(|e| StoreError::Internal(e.to_string()))?;
        read_response(resp)
    }

    fn patch<T: DeserializeOwned>(&self, path: &str, body: Value, auth: bool) -> StoreResult<T> {
        let mut req = self.client.patch(self.url(path));
        if auth {
            if let Some(token) = &self.access_token {
                req = req.bearer_auth(token);
            }
        }
        req = req.json(&body);
        let resp = req
            .send()
            .map_err(|e| StoreError::Internal(e.to_string()))?;
        read_response(resp)
    }
}

fn read_response<T: DeserializeOwned>(resp: reqwest::blocking::Response) -> StoreResult<T> {
    let status = resp.status();
    let text = resp
        .text()
        .map_err(|e| StoreError::Internal(e.to_string()))?;
    if !status.is_success() {
        let msg = if let Ok(payload) = serde_json::from_str::<ErrorPayload>(&text) {
            payload.error.unwrap_or_else(|| text.clone())
        } else if text.trim().is_empty() {
            format!("Backend request failed with status {status}.")
        } else {
            text.trim().to_string()
        };
        return Err(StoreError::Internal(msg));
    }
    serde_json::from_str(&text).map_err(|e| StoreError::Internal(e.to_string()))
}

impl Store for RemoteStore {
    fn get_device_config(&self, device_id: i64) -> StoreResult<Option<DeviceConfigState>> {
        let state: DeviceConfigState =
            self.get(&format!("/api/v1/devices/{device_id}/config"), true)?;
        Ok(Some(state))
    }

    fn upsert_device_config_mirror(
        &self,
        _device_id: i64,
        _revision: i64,
        _snapshot: Value,
        _updated_at: Option<String>,
    ) -> StoreResult<DeviceConfigState> {
        Err(StoreError::Internal(
            "Remote store does not support writing device mirrors directly".to_string(),
        ))
    }

    fn queue_device_config_command(
        &self,
        device_id: i64,
        base_revision: i64,
        summary: String,
        mutation: Value,
    ) -> StoreResult<DeviceConfigCommand> {
        let mut req = self
            .client
            .post(self.url(&format!("/api/v1/devices/{device_id}/config/commands")));
        if let Some(token) = &self.access_token {
            req = req.bearer_auth(token);
        }
        let resp = req
            .json(&json!({
                "baseRevision": base_revision,
                "summary": summary,
                "mutation": mutation,
            }))
            .send()
            .map_err(|e| StoreError::Internal(e.to_string()))?;
        let status = resp.status();
        let text = resp
            .text()
            .map_err(|e| StoreError::Internal(e.to_string()))?;
        if status == StatusCode::CONFLICT {
            let latest = self.get_device_config(device_id)?;
            let remote_revision = latest.map(|state| state.revision).unwrap_or(0);
            return Err(StoreError::Conflict {
                local: base_revision,
                remote: remote_revision,
            });
        }
        if !status.is_success() {
            let msg = if let Ok(payload) = serde_json::from_str::<ErrorPayload>(&text) {
                payload.error.unwrap_or_else(|| text.clone())
            } else if text.trim().is_empty() {
                format!("Backend request failed with status {status}.")
            } else {
                text.trim().to_string()
            };
            return Err(StoreError::Internal(msg));
        }
        serde_json::from_str(&text).map_err(StoreError::from)
    }

    fn list_device_config_audit_logs(
        &self,
        device_id: i64,
        limit: i32,
        offset: i32,
    ) -> StoreResult<Paginated<DeviceConfigAuditLog>> {
        let resp: DeviceConfigAuditLogsResponse = self.get(
            &format!("/api/v1/devices/{device_id}/config/audit?limit={limit}&offset={offset}"),
            true,
        )?;
        Ok(Paginated {
            items: resp.logs,
            limit: resp.limit,
            offset: resp.offset,
        })
    }

    fn replace_device_config_pending_commands(
        &self,
        _device_id: i64,
        _commands: Vec<DeviceConfigCommand>,
    ) -> StoreResult<()> {
        Err(StoreError::Internal(
            "Remote store does not support replacing pending device commands locally".to_string(),
        ))
    }

    fn clear_device_config_pending_commands(&self, _device_id: i64) -> StoreResult<()> {
        Err(StoreError::Internal(
            "Remote store does not support clearing pending device commands locally".to_string(),
        ))
    }

    fn list_devices(&self) -> StoreResult<Vec<Device>> {
        let resp: DevicesResponse = self.get("/api/v1/devices", true)?;
        Ok(resp.devices)
    }

    fn patch_device(
        &self,
        device_id: i64,
        display_name: Option<&str>,
        enabled: Option<bool>,
    ) -> StoreResult<Value> {
        let mut body = json!({});
        if let Some(name) = display_name {
            body["displayName"] = json!(name);
        }
        if let Some(en) = enabled {
            body["enabled"] = json!(en);
        }
        self.patch(&format!("/api/v1/devices/{device_id}"), body, true)
    }

    fn revoke_device(&self, device_id: i64) -> StoreResult<Value> {
        self.post(&format!("/api/v1/devices/{device_id}/revoke"), None, true)
    }

    fn create_bind_code(&self) -> StoreResult<BindCode> {
        let resp: BindCodeResponse = self.post("/api/v1/devices/bind-codes", None, true)?;
        Ok(BindCode {
            code: resp.code,
            expires_at: resp.expires_at,
        })
    }

    fn upsert_devices(&self, _devices: Vec<Device>) -> StoreResult<()> {
        // Remote store is read-only for sync; devices are managed by the backend
        Ok(())
    }

    fn list_records(&self, limit: i32, device_id: Option<i64>) -> StoreResult<Paginated<Record>> {
        let mut url = format!("/api/v1/records?limit={limit}");
        if let Some(did) = device_id {
            url.push_str(&format!("&device_id={did}"));
        }
        let resp: RecordsResponse = self.get(&url, true)?;
        Ok(Paginated {
            items: resp.records,
            limit: resp.limit,
            offset: resp.offset,
        })
    }

    fn get_record(&self, record_id: i64) -> StoreResult<Record> {
        self.get(&format!("/api/v1/records/{record_id}"), true)
    }

    fn upsert_records(&self, _records: Vec<Record>) -> StoreResult<()> {
        // Remote store is read-only for sync; records are managed by the backend
        Ok(())
    }

    fn get_system_info(&self) -> StoreResult<SystemInfo> {
        self.get("/api/v1/system/info", false)
    }
}
