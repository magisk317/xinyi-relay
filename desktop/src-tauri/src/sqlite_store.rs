use rusqlite::{params, Connection};
use serde_json::{json, Value};
use std::sync::Mutex;

use super::store::*;

pub struct SqliteStore {
    conn: Mutex<Connection>,
}

impl SqliteStore {
    pub fn open(path: &std::path::Path) -> StoreResult<Self> {
        let conn = Connection::open(path)?;
        let store = SqliteStore { conn: Mutex::new(conn) };
        store.migrate()?;
        Ok(store)
    }

    #[allow(dead_code)]
    pub fn open_in_memory() -> StoreResult<Self> {
        let conn = Connection::open_in_memory()?;
        let store = SqliteStore { conn: Mutex::new(conn) };
        store.migrate()?;
        Ok(store)
    }

    fn migrate(&self) -> StoreResult<()> {
        let conn = self.conn.lock().map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        conn.execute_batch(
            "
            CREATE TABLE IF NOT EXISTS config_snapshots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                revision INTEGER NOT NULL,
                content TEXT NOT NULL DEFAULT '{}',
                updated_by_type TEXT NOT NULL DEFAULT 'desktop',
                updated_by_id INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                UNIQUE(revision)
            );

            CREATE TABLE IF NOT EXISTS config_audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                revision INTEGER NOT NULL,
                actor_type TEXT NOT NULL DEFAULT 'desktop',
                actor_id INTEGER NOT NULL DEFAULT 0,
                summary TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            );

            CREATE TABLE IF NOT EXISTS devices (
                id INTEGER PRIMARY KEY,
                user_id INTEGER NOT NULL DEFAULT 0,
                device_name TEXT NOT NULL DEFAULT '',
                device_model TEXT NOT NULL DEFAULT '',
                platform TEXT NOT NULL DEFAULT 'android',
                app_version TEXT NOT NULL DEFAULT '',
                display_name TEXT NOT NULL DEFAULT '',
                enabled INTEGER NOT NULL DEFAULT 1,
                revoked_at TEXT,
                last_seen_at TEXT,
                local_addresses TEXT NOT NULL DEFAULT '[]',
                capabilities TEXT NOT NULL DEFAULT '{}',
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now'))
            );

            CREATE TABLE IF NOT EXISTS relay_records (
                id INTEGER PRIMARY KEY,
                user_id INTEGER NOT NULL DEFAULT 0,
                device_id INTEGER NOT NULL DEFAULT 0,
                event_id TEXT,
                record_type TEXT NOT NULL DEFAULT '',
                sender TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL DEFAULT '',
                sms_code TEXT NOT NULL DEFAULT '',
                package_name TEXT NOT NULL DEFAULT '',
                msg_type INTEGER NOT NULL DEFAULT 0,
                call_type INTEGER NOT NULL DEFAULT 0,
                occurred_at TEXT NOT NULL,
                uploaded_at TEXT NOT NULL DEFAULT (datetime('now')),
                metadata TEXT NOT NULL DEFAULT '{}'
            );

            CREATE UNIQUE INDEX IF NOT EXISTS relay_records_device_event_id_idx
                ON relay_records(device_id, event_id)
                WHERE event_id IS NOT NULL;
            ",
        )?;
        Ok(())
    }
}

impl Store for SqliteStore {
    fn get_config_snapshot(&self) -> StoreResult<Option<ConfigSnapshot>> {
        let conn = self.conn.lock().map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        let mut stmt = conn.prepare(
            "SELECT revision, content, updated_at FROM config_snapshots ORDER BY revision DESC LIMIT 1",
        )?;
        let mut rows = stmt.query_map([], |row| {
            Ok(ConfigSnapshot {
                revision: row.get(0)?,
                snapshot: {
                    let text: String = row.get(1)?;
                    serde_json::from_str(&text).unwrap_or(json!({}))
                },
                updated_at: row.get(2)?,
            })
        })?;
        match rows.next() {
            Some(row) => Ok(Some(row?)),
            None => Ok(None),
        }
    }

    fn put_config_snapshot(&self, base_revision: i64, content: Value) -> StoreResult<ConfigSnapshot> {
        let conn = self.conn.lock().map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        let current_revision: i64 = conn
            .query_row(
                "SELECT COALESCE(MAX(revision), 0) FROM config_snapshots",
                [],
                |row| row.get(0),
            )
            .unwrap_or(0);

        if current_revision != base_revision {
            return Err(StoreError::Conflict {
                local: current_revision,
                remote: base_revision,
            });
        }

        let new_revision = current_revision + 1;
        let content_text = serde_json::to_string(&content)?;
        let now = chrono::Utc::now().to_rfc3339();
        conn.execute(
            "INSERT INTO config_snapshots (revision, content, updated_by_type, updated_at) VALUES (?1, ?2, 'desktop', ?3)",
            params![new_revision, content_text, now],
        )?;
        conn.execute(
            "INSERT INTO config_audit_logs (revision, actor_type, summary) VALUES (?1, 'desktop', ?2)",
            params![new_revision, format!("Config updated to revision {}", new_revision)],
        )?;

        Ok(ConfigSnapshot {
            revision: new_revision,
            snapshot: content,
            updated_at: Some(now),
        })
    }

    fn list_config_audit_logs(&self, limit: i32, offset: i32) -> StoreResult<Paginated<ConfigAuditLog>> {
        let conn = self.conn.lock().map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        let mut stmt = conn.prepare(
            "SELECT id, revision, actor_type, actor_id, summary, created_at FROM config_audit_logs ORDER BY id DESC LIMIT ?1 OFFSET ?2",
        )?;
        let logs = stmt
            .query_map(params![limit, offset], |row| {
                Ok(ConfigAuditLog {
                    id: row.get(0)?,
                    revision: row.get(1)?,
                    actor_type: row.get(2)?,
                    actor_id: row.get(3)?,
                    summary: row.get(4)?,
                    created_at: row.get(5)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;

        Ok(Paginated {
            items: logs,
            limit,
            offset,
        })
    }

    fn list_devices(&self) -> StoreResult<Vec<Device>> {
        let conn = self.conn.lock().map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        let mut stmt = conn.prepare(
            "SELECT id, user_id, device_name, device_model, platform, app_version, display_name, enabled, revoked_at, last_seen_at, local_addresses, capabilities, created_at, updated_at FROM devices ORDER BY id",
        )?;
        let devices = stmt
            .query_map([], |row| {
                Ok(Device {
                    id: row.get(0)?,
                    user_id: row.get(1)?,
                    device_name: row.get(2)?,
                    device_model: row.get(3)?,
                    platform: row.get(4)?,
                    app_version: row.get(5)?,
                    display_name: row.get(6)?,
                    enabled: row.get::<_, i32>(7)? != 0,
                    revoked_at: row.get(8)?,
                    last_seen_at: row.get(9)?,
                    local_addresses: {
                        let text: String = row.get(10)?;
                        serde_json::from_str(&text).unwrap_or(json!([]))
                    },
                    capabilities: {
                        let text: String = row.get(11)?;
                        serde_json::from_str(&text).unwrap_or(json!({}))
                    },
                    created_at: row.get(12)?,
                    updated_at: row.get(13)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;

        Ok(devices)
    }

    fn patch_device(&self, device_id: i64, display_name: Option<&str>, enabled: Option<bool>) -> StoreResult<Value> {
        let conn = self.conn.lock().map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        if let Some(name) = display_name {
            conn.execute(
                "UPDATE devices SET display_name = ?1, updated_at = datetime('now') WHERE id = ?2",
                params![name, device_id],
            )?;
        }
        if let Some(en) = enabled {
            conn.execute(
                "UPDATE devices SET enabled = ?1, updated_at = datetime('now') WHERE id = ?2",
                params![en as i32, device_id],
            )?;
        }
        Ok(json!({ "ok": true }))
    }

    fn revoke_device(&self, device_id: i64) -> StoreResult<Value> {
        let conn = self.conn.lock().map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        conn.execute(
            "UPDATE devices SET revoked_at = datetime('now'), updated_at = datetime('now') WHERE id = ?1",
            params![device_id],
        )?;
        Ok(json!({ "ok": true }))
    }

    fn create_bind_code(&self) -> StoreResult<BindCode> {
        use uuid::Uuid;
        let code = Uuid::new_v4().to_string();
        let expires_at = chrono::Utc::now() + chrono::Duration::minutes(10);
        Ok(BindCode {
            code,
            expires_at: expires_at.to_rfc3339(),
        })
    }

    fn upsert_devices(&self, devices: Vec<Device>) -> StoreResult<()> {
        let conn = self.conn.lock().map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        for d in &devices {
            conn.execute(
                "INSERT INTO devices (id, user_id, device_name, device_model, platform, app_version, display_name, enabled, revoked_at, last_seen_at, local_addresses, capabilities, created_at, updated_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)
                 ON CONFLICT(id) DO UPDATE SET
                     user_id=excluded.user_id, device_name=excluded.device_name, device_model=excluded.device_model,
                     platform=excluded.platform, app_version=excluded.app_version, display_name=excluded.display_name,
                     enabled=excluded.enabled, revoked_at=excluded.revoked_at, last_seen_at=excluded.last_seen_at,
                     local_addresses=excluded.local_addresses, capabilities=excluded.capabilities, updated_at=excluded.updated_at",
                rusqlite::params![
                    d.id, d.user_id, d.device_name, d.device_model, d.platform, d.app_version,
                    d.display_name, d.enabled as i32, d.revoked_at, d.last_seen_at,
                    serde_json::to_string(&d.local_addresses).unwrap_or_else(|_| "[]".to_string()),
                    serde_json::to_string(&d.capabilities).unwrap_or_else(|_| "{}".to_string()),
                    d.created_at, d.updated_at,
                ],
            )?;
        }
        Ok(())
    }

    fn list_records(&self, limit: i32, device_id: Option<i64>) -> StoreResult<Paginated<Record>> {
        let conn = self.conn.lock().map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        let (sql, param_values): (String, Vec<Box<dyn rusqlite::types::ToSql>>) = match device_id {
            Some(did) => (
                "SELECT id, device_id, event_id, record_type, sender, body, sms_code, package_name, metadata, msg_type, call_type, occurred_at, uploaded_at FROM relay_records WHERE device_id = ?1 ORDER BY id DESC LIMIT ?2".to_string(),
                vec![Box::new(did), Box::new(limit)],
            ),
            None => (
                "SELECT id, device_id, event_id, record_type, sender, body, sms_code, package_name, metadata, msg_type, call_type, occurred_at, uploaded_at FROM relay_records ORDER BY id DESC LIMIT ?1".to_string(),
                vec![Box::new(limit)],
            ),
        };

        let mut stmt = conn.prepare(&sql)?;
        let params_ref: Vec<&dyn rusqlite::types::ToSql> = param_values.iter().map(|p| p.as_ref()).collect();
        let records = stmt
            .query_map(params_ref.as_slice(), |row| {
                Ok(Record {
                    id: row.get(0)?,
                    device_id: row.get(1)?,
                    event_id: row.get(2)?,
                    record_type: row.get(3)?,
                    sender: row.get(4)?,
                    body: row.get(5)?,
                    sms_code: row.get(6)?,
                    package_name: row.get(7)?,
                    metadata: {
                        let text: String = row.get(8)?;
                        serde_json::from_str(&text).unwrap_or(json!({}))
                    },
                    msg_type: row.get(9)?,
                    call_type: row.get(10)?,
                    occurred_at: row.get(11)?,
                    uploaded_at: row.get(12)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;

        Ok(Paginated {
            items: records,
            limit,
            offset: 0,
        })
    }

    fn get_record(&self, record_id: i64) -> StoreResult<Record> {
        let conn = self.conn.lock().map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        let mut stmt = conn.prepare(
            "SELECT id, device_id, event_id, record_type, sender, body, sms_code, package_name, metadata, msg_type, call_type, occurred_at, uploaded_at FROM relay_records WHERE id = ?1",
        )?;
        let mut rows = stmt.query_map(params![record_id], |row| {
            Ok(Record {
                id: row.get(0)?,
                device_id: row.get(1)?,
                event_id: row.get(2)?,
                record_type: row.get(3)?,
                sender: row.get(4)?,
                body: row.get(5)?,
                sms_code: row.get(6)?,
                package_name: row.get(7)?,
                metadata: {
                    let text: String = row.get(8)?;
                    serde_json::from_str(&text).unwrap_or(json!({}))
                },
                msg_type: row.get(9)?,
                call_type: row.get(10)?,
                occurred_at: row.get(11)?,
                uploaded_at: row.get(12)?,
            })
        })?;
        match rows.next() {
            Some(row) => Ok(row?),
            None => Err(StoreError::Internal(format!("Record {} not found", record_id))),
        }
    }

    fn upsert_records(&self, records: Vec<Record>) -> StoreResult<()> {
        let conn = self.conn.lock().map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        for r in &records {
            conn.execute(
                "INSERT INTO relay_records (id, user_id, device_id, event_id, record_type, sender, body, sms_code, package_name, msg_type, call_type, occurred_at, uploaded_at, metadata)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)
                 ON CONFLICT(id) DO UPDATE SET
                     device_id=excluded.device_id, event_id=excluded.event_id, record_type=excluded.record_type,
                     sender=excluded.sender, body=excluded.body, sms_code=excluded.sms_code,
                     package_name=excluded.package_name, msg_type=excluded.msg_type, call_type=excluded.call_type,
                     occurred_at=excluded.occurred_at, uploaded_at=excluded.uploaded_at, metadata=excluded.metadata",
                rusqlite::params![
                    r.id, 0i64, r.device_id, r.event_id, r.record_type, r.sender, r.body, r.sms_code,
                    r.package_name, r.msg_type, r.call_type, r.occurred_at, r.uploaded_at,
                    serde_json::to_string(&r.metadata).unwrap_or_else(|_| "{}".to_string()),
                ],
            )?;
        }
        Ok(())
    }

    fn get_system_info(&self) -> StoreResult<SystemInfo> {
        let conn = self.conn.lock().map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        let device_count: i64 = conn.query_row(
            "SELECT COUNT(*) FROM devices WHERE revoked_at IS NULL",
            [],
            |row| row.get(0),
        )?;
        Ok(SystemInfo {
            service: "xinyi-relay-desktop".to_string(),
            app_env: "local".to_string(),
            local_base_url: "local://sqlite".to_string(),
            public_base_url: String::new(),
            database_ready: true,
            user_count: device_count,
            time: chrono::Utc::now().to_rfc3339(),
        })
    }
}
