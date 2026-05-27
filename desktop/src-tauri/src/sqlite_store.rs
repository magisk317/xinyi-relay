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

#[cfg(test)]
mod tests {
    use super::*;

    fn test_store() -> SqliteStore {
        SqliteStore::open_in_memory().unwrap()
    }

    #[test]
    fn config_snapshot_empty_by_default() {
        let store = test_store();
        assert!(store.get_config_snapshot().unwrap().is_none());
    }

    #[test]
    fn config_snapshot_put_and_get() {
        let store = test_store();
        let snap = store.put_config_snapshot(0, json!({"key": "value"})).unwrap();
        assert_eq!(snap.revision, 1);
        assert!(snap.updated_at.is_some());

        let loaded = store.get_config_snapshot().unwrap().unwrap();
        assert_eq!(loaded.revision, 1);
        assert_eq!(loaded.snapshot, json!({"key": "value"}));
    }

    #[test]
    fn config_snapshot_conflict_on_stale_base() {
        let store = test_store();
        store.put_config_snapshot(0, json!({"v": 1})).unwrap();
        let err = store.put_config_snapshot(0, json!({"v": 2})).unwrap_err();
        assert!(matches!(err, StoreError::Conflict { local: 1, remote: 0 }));
    }

    #[test]
    fn config_snapshot_revision_increments() {
        let store = test_store();
        store.put_config_snapshot(0, json!({"v": 1})).unwrap();
        store.put_config_snapshot(1, json!({"v": 2})).unwrap();
        let snap = store.put_config_snapshot(2, json!({"v": 3})).unwrap();
        assert_eq!(snap.revision, 3);
    }

    #[test]
    fn devices_empty_by_default() {
        let store = test_store();
        assert!(store.list_devices().unwrap().is_empty());
    }

    #[test]
    fn devices_upsert_and_list() {
        let store = test_store();
        let devices = vec![
            Device {
                id: 1, user_id: 0, device_name: "Phone".to_string(),
                device_model: "Pixel".to_string(), platform: "android".to_string(),
                app_version: "1.0".to_string(), display_name: "My Phone".to_string(),
                enabled: true, revoked_at: None, last_seen_at: None,
                local_addresses: json!([]), capabilities: json!({}),
                created_at: "2026-01-01T00:00:00Z".to_string(),
                updated_at: "2026-01-01T00:00:00Z".to_string(),
            },
            Device {
                id: 2, user_id: 0, device_name: "Tablet".to_string(),
                device_model: "iPad".to_string(), platform: "ios".to_string(),
                app_version: "1.0".to_string(), display_name: "My Tablet".to_string(),
                enabled: true, revoked_at: None, last_seen_at: None,
                local_addresses: json!([]), capabilities: json!({}),
                created_at: "2026-01-01T00:00:00Z".to_string(),
                updated_at: "2026-01-01T00:00:00Z".to_string(),
            },
        ];
        store.upsert_devices(devices).unwrap();
        let listed = store.list_devices().unwrap();
        assert_eq!(listed.len(), 2);
        assert_eq!(listed[0].device_name, "Phone");
        assert_eq!(listed[1].device_name, "Tablet");
    }

    #[test]
    fn devices_upsert_updates_existing() {
        let store = test_store();
        let device = Device {
            id: 1, user_id: 0, device_name: "Phone".to_string(),
            device_model: "Pixel".to_string(), platform: "android".to_string(),
            app_version: "1.0".to_string(), display_name: "Old Name".to_string(),
            enabled: true, revoked_at: None, last_seen_at: None,
            local_addresses: json!([]), capabilities: json!({}),
            created_at: "2026-01-01T00:00:00Z".to_string(),
            updated_at: "2026-01-01T00:00:00Z".to_string(),
        };
        store.upsert_devices(vec![device]).unwrap();

        let updated = Device {
            id: 1, user_id: 0, device_name: "Phone".to_string(),
            device_model: "Pixel".to_string(), platform: "android".to_string(),
            app_version: "1.0".to_string(), display_name: "New Name".to_string(),
            enabled: false, revoked_at: None, last_seen_at: None,
            local_addresses: json!([]), capabilities: json!({}),
            created_at: "2026-01-01T00:00:00Z".to_string(),
            updated_at: "2026-01-02T00:00:00Z".to_string(),
        };
        store.upsert_devices(vec![updated]).unwrap();

        let listed = store.list_devices().unwrap();
        assert_eq!(listed.len(), 1);
        assert_eq!(listed[0].display_name, "New Name");
        assert!(!listed[0].enabled);
    }

    #[test]
    fn records_empty_by_default() {
        let store = test_store();
        let result = store.list_records(10, None).unwrap();
        assert!(result.items.is_empty());
    }

    #[test]
    fn records_upsert_and_list() {
        let store = test_store();
        let records = vec![
            Record {
                id: 1, device_id: 1, event_id: Some("evt1".to_string()),
                record_type: "sms".to_string(), sender: "Bank".to_string(),
                body: "Code 123456".to_string(), sms_code: "123456".to_string(),
                package_name: "com.sms".to_string(), metadata: json!({}),
                msg_type: 0, call_type: 0,
                occurred_at: "2026-01-01T00:00:00Z".to_string(),
                uploaded_at: "2026-01-01T00:00:00Z".to_string(),
            },
        ];
        store.upsert_records(records).unwrap();
        let result = store.list_records(10, None).unwrap();
        assert_eq!(result.items.len(), 1);
        assert_eq!(result.items[0].sms_code, "123456");
    }

    #[test]
    fn records_upsert_updates_existing() {
        let store = test_store();
        let record = Record {
            id: 1, device_id: 1, event_id: Some("evt1".to_string()),
            record_type: "sms".to_string(), sender: "Bank".to_string(),
            body: "Old body".to_string(), sms_code: "111".to_string(),
            package_name: "com.sms".to_string(), metadata: json!({}),
            msg_type: 0, call_type: 0,
            occurred_at: "2026-01-01T00:00:00Z".to_string(),
            uploaded_at: "2026-01-01T00:00:00Z".to_string(),
        };
        store.upsert_records(vec![record]).unwrap();

        let updated = Record {
            id: 1, device_id: 1, event_id: Some("evt1".to_string()),
            record_type: "sms".to_string(), sender: "Bank".to_string(),
            body: "New body".to_string(), sms_code: "222".to_string(),
            package_name: "com.sms".to_string(), metadata: json!({}),
            msg_type: 0, call_type: 0,
            occurred_at: "2026-01-01T00:00:00Z".to_string(),
            uploaded_at: "2026-01-02T00:00:00Z".to_string(),
        };
        store.upsert_records(vec![updated]).unwrap();

        let result = store.list_records(10, None).unwrap();
        assert_eq!(result.items.len(), 1);
        assert_eq!(result.items[0].sms_code, "222");
        assert_eq!(result.items[0].body, "New body");
    }

    #[test]
    fn system_info_reports_device_count() {
        let store = test_store();
        let info = store.get_system_info().unwrap();
        assert_eq!(info.user_count, 0);

        let device = Device {
            id: 1, user_id: 0, device_name: "Phone".to_string(),
            device_model: "".to_string(), platform: "android".to_string(),
            app_version: "".to_string(), display_name: "".to_string(),
            enabled: true, revoked_at: None, last_seen_at: None,
            local_addresses: json!([]), capabilities: json!({}),
            created_at: "2026-01-01T00:00:00Z".to_string(),
            updated_at: "2026-01-01T00:00:00Z".to_string(),
        };
        store.upsert_devices(vec![device]).unwrap();

        let info = store.get_system_info().unwrap();
        assert_eq!(info.user_count, 1);
    }
}
