use reqwest::blocking::Client;
use serde::de::DeserializeOwned;
use serde_json::{json, Value};

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
struct ConfigAuditLogsResponse {
    logs: Vec<ConfigAuditLog>,
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
        let resp = req.send().map_err(|e| StoreError::Internal(e.to_string()))?;
        read_response(resp)
    }

    fn post<T: DeserializeOwned>(&self, path: &str, body: Option<Value>, auth: bool) -> StoreResult<T> {
        let mut req = self.client.post(self.url(path));
        if auth {
            if let Some(token) = &self.access_token {
                req = req.bearer_auth(token);
            }
        }
        if let Some(b) = body {
            req = req.json(&b);
        }
        let resp = req.send().map_err(|e| StoreError::Internal(e.to_string()))?;
        read_response(resp)
    }

    fn put<T: DeserializeOwned>(&self, path: &str, body: Value, auth: bool) -> StoreResult<T> {
        let mut req = self.client.put(self.url(path));
        if auth {
            if let Some(token) = &self.access_token {
                req = req.bearer_auth(token);
            }
        }
        req = req.json(&body);
        let resp = req.send().map_err(|e| StoreError::Internal(e.to_string()))?;
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
        let resp = req.send().map_err(|e| StoreError::Internal(e.to_string()))?;
        read_response(resp)
    }
}

fn read_response<T: DeserializeOwned>(resp: reqwest::blocking::Response) -> StoreResult<T> {
    let status = resp.status();
    let text = resp.text().map_err(|e| StoreError::Internal(e.to_string()))?;
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
    fn get_config_snapshot(&self) -> StoreResult<Option<ConfigSnapshot>> {
        let snapshot: ConfigSnapshot = self.get("/api/v1/config/snapshot", true)?;
        Ok(Some(snapshot))
    }

    fn put_config_snapshot(&self, base_revision: i64, content: Value) -> StoreResult<ConfigSnapshot> {
        self.put(
            "/api/v1/config/snapshot",
            json!({ "base_revision": base_revision, "snapshot": content }),
            true,
        )
    }

    fn list_config_audit_logs(&self, limit: i32, offset: i32) -> StoreResult<Paginated<ConfigAuditLog>> {
        let resp: ConfigAuditLogsResponse = self.get(
            &format!("/api/v1/config/audit?limit={limit}&offset={offset}"),
            true,
        )?;
        Ok(Paginated {
            items: resp.logs,
            limit: resp.limit,
            offset: resp.offset,
        })
    }

    fn list_devices(&self) -> StoreResult<Vec<Device>> {
        let resp: DevicesResponse = self.get("/api/v1/devices", true)?;
        Ok(resp.devices)
    }

    fn patch_device(&self, device_id: i64, display_name: Option<&str>, enabled: Option<bool>) -> StoreResult<Value> {
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
