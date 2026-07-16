use rusqlite::{params, Connection, OptionalExtension};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::collections::HashSet;
use std::sync::Mutex;

use super::store::*;

pub struct SqliteStore {
    conn: Mutex<Connection>,
}

pub(crate) struct RecordSyncResult {
    pub(crate) inserted: usize,
    pub(crate) updated: usize,
    pub(crate) deleted: usize,
}

impl SqliteStore {
    pub fn open(path: &std::path::Path) -> StoreResult<Self> {
        let conn = Connection::open(path)?;
        let store = SqliteStore {
            conn: Mutex::new(conn),
        };
        store.migrate()?;
        Ok(store)
    }

    #[allow(dead_code)]
    pub fn open_in_memory() -> StoreResult<Self> {
        let conn = Connection::open_in_memory()?;
        let store = SqliteStore {
            conn: Mutex::new(conn),
        };
        store.migrate()?;
        Ok(store)
    }

    fn migrate(&self) -> StoreResult<()> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        conn.execute_batch(
            "
            CREATE TABLE IF NOT EXISTS device_config_commands (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id INTEGER NOT NULL,
                base_revision INTEGER NOT NULL,
                target_revision INTEGER NOT NULL,
                mutation TEXT NOT NULL DEFAULT '{}',
                summary TEXT NOT NULL DEFAULT '',
                actor_type TEXT NOT NULL DEFAULT 'desktop',
                actor_id INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'pending',
                failure_reason TEXT,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                updated_at TEXT NOT NULL DEFAULT (datetime('now')),
                applied_at TEXT
            );

            CREATE TABLE IF NOT EXISTS device_config_mirrors (
                device_id INTEGER PRIMARY KEY,
                revision INTEGER NOT NULL DEFAULT 0,
                snapshot TEXT NOT NULL DEFAULT '{}',
                updated_at TEXT
            );

            CREATE TABLE IF NOT EXISTS device_config_audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id INTEGER NOT NULL,
                command_id INTEGER,
                revision INTEGER NOT NULL DEFAULT 0,
                event_type TEXT NOT NULL DEFAULT '',
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

			CREATE TABLE IF NOT EXISTS local_device_bind_codes (
				code_hash TEXT PRIMARY KEY,
				expires_at TEXT NOT NULL,
				used_at TEXT
			);

			CREATE TABLE IF NOT EXISTS local_device_tokens (
				device_id INTEGER PRIMARY KEY,
				token_hash TEXT NOT NULL UNIQUE,
				revoked_at TEXT
			);
            ",
        )?;
        Self::seed_device_mirrors_from_legacy_snapshot(&conn, None)?;
        Ok(())
    }

    fn seed_device_mirrors_from_legacy_snapshot(
        conn: &Connection,
        device_ids: Option<&[i64]>,
    ) -> StoreResult<()> {
        let has_legacy_snapshot_table: Option<i64> = conn
            .query_row(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'config_snapshots' LIMIT 1",
                [],
                |row| row.get(0),
            )
            .optional()?;
        if has_legacy_snapshot_table.is_none() {
            return Ok(());
        }

        let mirror_count: i64 =
            conn.query_row("SELECT COUNT(*) FROM device_config_mirrors", [], |row| {
                row.get(0)
            })?;
        if mirror_count > 0 {
            return Ok(());
        }

        let legacy_snapshot = conn
            .query_row(
                "SELECT revision, content, updated_at FROM config_snapshots ORDER BY revision DESC LIMIT 1",
                [],
                |row| {
                    Ok(DeviceConfigMirror {
                        revision: row.get(0)?,
                        snapshot: {
                            let text: String = row.get(1)?;
                            serde_json::from_str(&text).unwrap_or(json!({}))
                        },
                        updated_at: row.get(2)?,
                    })
                },
            )
            .optional()?;
        let Some(legacy_snapshot) = legacy_snapshot else {
            return Ok(());
        };

        let target_device_ids = if let Some(device_ids) = device_ids {
            device_ids.to_vec()
        } else {
            let mut stmt = conn.prepare("SELECT id FROM devices ORDER BY id ASC")?;
            stmt.query_map([], |row| row.get::<_, i64>(0))?
                .collect::<Result<Vec<_>, _>>()?
        };
        if target_device_ids.is_empty() {
            return Ok(());
        }

        let snapshot_text = serde_json::to_string(&legacy_snapshot.snapshot)?;
        for device_id in target_device_ids {
            conn.execute(
                "INSERT OR IGNORE INTO device_config_mirrors (device_id, revision, snapshot, updated_at)
                 VALUES (?1, ?2, ?3, ?4)",
                params![
                    device_id,
                    legacy_snapshot.revision,
                    snapshot_text.as_str(),
                    legacy_snapshot.updated_at.as_deref()
                ],
            )?;
        }
        Ok(())
    }
}

fn hash_secret(value: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(value.as_bytes());
    format!("{:x}", hasher.finalize())
}

impl SqliteStore {
    pub(crate) fn authenticate_local_device(&self, token: &str) -> StoreResult<Option<i64>> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        let device_id = conn
            .query_row(
                "SELECT d.id
                   FROM local_device_tokens t
                   JOIN devices d ON d.id = t.device_id
                  WHERE t.token_hash = ?1 AND t.revoked_at IS NULL
                    AND d.revoked_at IS NULL AND d.enabled = 1",
                params![hash_secret(token)],
                |row| row.get(0),
            )
            .optional()?;
        Ok(device_id)
    }

    pub(crate) fn register_local_device(
        &self,
        bind_code: &str,
        device_name: &str,
        device_model: &str,
        platform: &str,
        app_version: &str,
        device_token: &str,
    ) -> StoreResult<Option<Device>> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        conn.execute_batch("BEGIN IMMEDIATE")?;
        let result = (|| -> StoreResult<Option<Device>> {
            let now = chrono::Utc::now().to_rfc3339();
            let consumed = conn.execute(
                "UPDATE local_device_bind_codes SET used_at = ?2
                  WHERE code_hash = ?1 AND used_at IS NULL AND expires_at > ?2",
                params![hash_secret(bind_code), now],
            )?;
            if consumed != 1 {
                return Ok(None);
            }
            let device_id: i64 =
                conn.query_row("SELECT COALESCE(MAX(id), 0) + 1 FROM devices", [], |row| {
                    row.get(0)
                })?;
            conn.execute(
                "INSERT INTO devices (
                    id, user_id, device_name, device_model, platform, app_version, display_name,
                    enabled, local_addresses, capabilities, created_at, updated_at
                 ) VALUES (?1, 1, ?2, ?3, ?4, ?5, ?2, 1, '[]', '{}', ?6, ?6)",
                params![
                    device_id,
                    device_name,
                    device_model,
                    platform,
                    app_version,
                    now
                ],
            )?;
            conn.execute(
                "INSERT INTO local_device_tokens (device_id, token_hash) VALUES (?1, ?2)",
                params![device_id, hash_secret(device_token)],
            )?;
            Ok(Some(Device {
                id: device_id,
                user_id: 1,
                device_name: device_name.to_string(),
                device_model: device_model.to_string(),
                platform: platform.to_string(),
                app_version: app_version.to_string(),
                display_name: device_name.to_string(),
                enabled: true,
                revoked_at: None,
                last_seen_at: None,
                local_addresses: json!([]),
                capabilities: json!({}),
                created_at: now.clone(),
                updated_at: now,
            }))
        })();
        if result.is_ok() {
            conn.execute_batch("COMMIT")?;
        } else {
            let _ = conn.execute_batch("ROLLBACK");
        }
        result
    }

    pub(crate) fn update_local_device_heartbeat(
        &self,
        device_id: i64,
        app_version: &str,
        local_addresses: &Value,
        capabilities: &Value,
    ) -> StoreResult<()> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        conn.execute(
            "UPDATE devices SET app_version=?2, local_addresses=?3, capabilities=?4,
             last_seen_at=?5, updated_at=?5 WHERE id=?1 AND revoked_at IS NULL",
            params![
                device_id,
                app_version,
                serde_json::to_string(local_addresses)?,
                serde_json::to_string(capabilities)?,
                chrono::Utc::now().to_rfc3339(),
            ],
        )?;
        Ok(())
    }

    pub(crate) fn ack_local_device_config_command(
        &self,
        device_id: i64,
        command_id: i64,
        status: &str,
        applied_revision: i64,
        failure_reason: &str,
        snapshot: &Value,
    ) -> StoreResult<DeviceConfigCommand> {
        if status != "applied" && status != "failed" {
            return Err(StoreError::Internal(
                "unsupported command status".to_string(),
            ));
        }
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        conn.execute_batch("BEGIN IMMEDIATE")?;
        let result = (|| -> StoreResult<DeviceConfigCommand> {
            let now = chrono::Utc::now().to_rfc3339();
            let applied_at = (status == "applied").then_some(now.clone());
            let changed = conn.execute(
                "UPDATE device_config_commands
                    SET status=?3, failure_reason=?4, updated_at=?5, applied_at=?6
                  WHERE id=?1 AND device_id=?2 AND status='pending'",
                params![
                    command_id,
                    device_id,
                    status,
                    failure_reason,
                    now,
                    applied_at
                ],
            )?;
            if changed != 1 {
                return Err(StoreError::Conflict {
                    local: command_id,
                    remote: command_id,
                });
            }
            if status == "applied" {
                conn.execute(
                    "INSERT INTO device_config_mirrors (device_id, revision, snapshot, updated_at)
                     VALUES (?1, ?2, ?3, ?4)
                     ON CONFLICT(device_id) DO UPDATE SET revision=excluded.revision,
                     snapshot=excluded.snapshot, updated_at=excluded.updated_at",
                    params![
                        device_id,
                        applied_revision,
                        serde_json::to_string(snapshot)?,
                        now,
                    ],
                )?;
            }
            read_device_config_command(&conn, device_id, command_id)
        })();
        if result.is_ok() {
            conn.execute_batch("COMMIT")?;
        } else {
            let _ = conn.execute_batch("ROLLBACK");
        }
        result
    }

    pub(crate) fn sync_local_device_records(
        &self,
        device_id: i64,
        records: &[Record],
        replace_existing: bool,
    ) -> StoreResult<RecordSyncResult> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        conn.execute_batch("BEGIN IMMEDIATE")?;
        let result = (|| -> StoreResult<RecordSyncResult> {
            let mut existing_event_ids = {
                let mut stmt = conn.prepare(
                    "SELECT event_id FROM relay_records WHERE device_id=?1 AND event_id IS NOT NULL",
                )?;
                stmt.query_map(params![device_id], |row| row.get::<_, String>(0))?
                    .collect::<Result<HashSet<_>, _>>()?
            };
            let incoming_event_ids = records
                .iter()
                .filter_map(|record| record.event_id.clone())
                .collect::<HashSet<_>>();
            let deleted = if replace_existing {
                let mut stmt =
                    conn.prepare("SELECT id, event_id FROM relay_records WHERE device_id=?1")?;
                let stale_ids = stmt
                    .query_map(params![device_id], |row| {
                        Ok((row.get::<_, i64>(0)?, row.get::<_, Option<String>>(1)?))
                    })?
                    .collect::<Result<Vec<_>, _>>()?
                    .into_iter()
                    .filter_map(|(id, event_id)| {
                        if event_id
                            .as_ref()
                            .is_some_and(|value| incoming_event_ids.contains(value))
                        {
                            None
                        } else {
                            Some(id)
                        }
                    })
                    .collect::<Vec<_>>();
                for id in &stale_ids {
                    conn.execute("DELETE FROM relay_records WHERE id=?1", params![id])?;
                }
                stale_ids.len()
            } else {
                0
            };
            let mut inserted = 0;
            let mut updated = 0;
            for record in records {
                let was_existing = record
                    .event_id
                    .as_ref()
                    .is_some_and(|event_id| existing_event_ids.contains(event_id));
                conn.execute(
                    "INSERT INTO relay_records (
                        user_id, device_id, event_id, record_type, sender, body, sms_code,
                        package_name, msg_type, call_type, occurred_at, uploaded_at, metadata
                     ) VALUES (1, ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)
                     ON CONFLICT(device_id, event_id) WHERE event_id IS NOT NULL DO UPDATE SET
                        record_type=excluded.record_type, sender=excluded.sender, body=excluded.body,
                        sms_code=excluded.sms_code, package_name=excluded.package_name,
                        msg_type=excluded.msg_type, call_type=excluded.call_type,
                        occurred_at=excluded.occurred_at, uploaded_at=excluded.uploaded_at,
                        metadata=excluded.metadata",
                    params![
                        device_id,
                        record.event_id,
                        record.record_type,
                        record.sender,
                        record.body,
                        record.sms_code,
                        record.package_name,
                        record.msg_type,
                        record.call_type,
                        record.occurred_at,
                        record.uploaded_at,
                        serde_json::to_string(&record.metadata)?,
                    ],
                )?;
                if was_existing {
                    updated += 1;
                } else {
                    inserted += 1;
                }
                if let Some(event_id) = &record.event_id {
                    existing_event_ids.insert(event_id.clone());
                }
            }
            Ok(RecordSyncResult {
                inserted,
                updated,
                deleted,
            })
        })();
        if result.is_ok() {
            conn.execute_batch("COMMIT")?;
        } else {
            let _ = conn.execute_batch("ROLLBACK");
        }
        result
    }
}

fn read_device_config_command(
    conn: &Connection,
    device_id: i64,
    command_id: i64,
) -> StoreResult<DeviceConfigCommand> {
    conn.query_row(
        "SELECT id, base_revision, target_revision, mutation, summary, actor_type, actor_id,
                status, failure_reason, created_at, updated_at, applied_at
           FROM device_config_commands WHERE id=?1 AND device_id=?2",
        params![command_id, device_id],
        |row| {
            let mutation: String = row.get(3)?;
            Ok(DeviceConfigCommand {
                id: row.get(0)?,
                base_revision: row.get(1)?,
                target_revision: row.get(2)?,
                mutation: serde_json::from_str(&mutation).unwrap_or(json!({})),
                summary: row.get(4)?,
                actor_type: row.get(5)?,
                actor_id: row.get(6)?,
                status: row.get(7)?,
                failure_reason: row.get(8)?,
                created_at: row.get(9)?,
                updated_at: row.get(10)?,
                applied_at: row.get(11)?,
            })
        },
    )
    .map_err(StoreError::from)
}

impl Store for SqliteStore {
    fn get_device_config(&self, device_id: i64) -> StoreResult<Option<DeviceConfigState>> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;

        let snapshot = conn
            .query_row(
                "SELECT revision, snapshot, updated_at FROM device_config_mirrors WHERE device_id = ?1",
                params![device_id],
                |row| {
                    Ok(DeviceConfigMirror {
                        revision: row.get(0)?,
                        snapshot: {
                            let text: String = row.get(1)?;
                            serde_json::from_str(&text).unwrap_or(json!({}))
                        },
                        updated_at: row.get(2)?,
                    })
                },
            )
            .optional()?
            .unwrap_or(DeviceConfigMirror {
                revision: 0,
                snapshot: json!({}),
                updated_at: None,
            });

        let mut stmt = conn.prepare(
            "SELECT id, base_revision, target_revision, mutation, summary, actor_type, actor_id, status, failure_reason, created_at, updated_at, applied_at
             FROM device_config_commands
             WHERE device_id = ?1 AND status = 'pending'
             ORDER BY created_at ASC, id ASC",
        )?;
        let pending_commands = stmt
            .query_map(params![device_id], |row| {
                Ok(DeviceConfigCommand {
                    id: row.get(0)?,
                    base_revision: row.get(1)?,
                    target_revision: row.get(2)?,
                    mutation: {
                        let text: String = row.get(3)?;
                        serde_json::from_str(&text).unwrap_or(json!({}))
                    },
                    summary: row.get(4)?,
                    actor_type: row.get(5)?,
                    actor_id: row.get(6)?,
                    status: row.get(7)?,
                    failure_reason: row.get(8)?,
                    created_at: row.get(9)?,
                    updated_at: row.get(10)?,
                    applied_at: row.get(11)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;

        Ok(Some(DeviceConfigState {
            device_id,
            revision: snapshot.revision,
            snapshot: snapshot.snapshot,
            pending_commands,
            updated_at: snapshot.updated_at,
        }))
    }

    fn upsert_device_config_mirror(
        &self,
        device_id: i64,
        revision: i64,
        snapshot: Value,
        updated_at: Option<String>,
    ) -> StoreResult<DeviceConfigState> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        let snapshot_text = serde_json::to_string(&snapshot)?;
        let timestamp = updated_at
            .clone()
            .unwrap_or_else(|| chrono::Utc::now().to_rfc3339());
        conn.execute(
            "INSERT INTO device_config_mirrors (device_id, revision, snapshot, updated_at)
             VALUES (?1, ?2, ?3, ?4)
             ON CONFLICT(device_id) DO UPDATE SET revision = excluded.revision, snapshot = excluded.snapshot, updated_at = excluded.updated_at",
            params![device_id, revision, snapshot_text, timestamp],
        )?;
        let mut stmt = conn.prepare(
            "SELECT id, base_revision, target_revision, mutation, summary, actor_type, actor_id, status, failure_reason, created_at, updated_at, applied_at
             FROM device_config_commands
             WHERE device_id = ?1 AND status = 'pending'
             ORDER BY created_at ASC, id ASC",
        )?;
        let pending_commands = stmt
            .query_map(params![device_id], |row| {
                Ok(DeviceConfigCommand {
                    id: row.get(0)?,
                    base_revision: row.get(1)?,
                    target_revision: row.get(2)?,
                    mutation: {
                        let text: String = row.get(3)?;
                        serde_json::from_str(&text).unwrap_or(json!({}))
                    },
                    summary: row.get(4)?,
                    actor_type: row.get(5)?,
                    actor_id: row.get(6)?,
                    status: row.get(7)?,
                    failure_reason: row.get(8)?,
                    created_at: row.get(9)?,
                    updated_at: row.get(10)?,
                    applied_at: row.get(11)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(DeviceConfigState {
            device_id,
            revision,
            snapshot,
            pending_commands,
            updated_at: updated_at.or(Some(timestamp)),
        })
    }

    fn queue_device_config_command(
        &self,
        device_id: i64,
        base_revision: i64,
        summary: String,
        mutation: Value,
    ) -> StoreResult<DeviceConfigCommand> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        let mirror_revision: i64 = match conn.query_row(
            "SELECT revision FROM device_config_mirrors WHERE device_id = ?1",
            params![device_id],
            |row| row.get(0),
        ) {
            Ok(value) => value,
            Err(rusqlite::Error::QueryReturnedNoRows) => 0,
            Err(err) => return Err(err.into()),
        };
        let latest_pending_target_revision: i64 = conn.query_row(
            "SELECT COALESCE(MAX(target_revision), 0) FROM device_config_commands WHERE device_id = ?1 AND status = 'pending'",
            params![device_id],
            |row| row.get(0),
        )?;
        let expected_base_revision = mirror_revision.max(latest_pending_target_revision);
        if expected_base_revision != base_revision {
            return Err(StoreError::Conflict {
                local: expected_base_revision,
                remote: base_revision,
            });
        }

        let target_revision = expected_base_revision + 1;
        let mutation_text = serde_json::to_string(&mutation)?;
        let now = chrono::Utc::now().to_rfc3339();
        conn.execute(
            "INSERT INTO device_config_commands (device_id, base_revision, target_revision, mutation, summary, actor_type, actor_id, status, created_at, updated_at)
             VALUES (?1, ?2, ?3, ?4, ?5, 'desktop', 0, 'pending', ?6, ?6)",
            params![device_id, base_revision, target_revision, mutation_text, summary, now],
        )?;
        let command_id = conn.last_insert_rowid();
        conn.execute(
            "INSERT INTO device_config_audit_logs (device_id, command_id, revision, event_type, actor_type, actor_id, summary, created_at)
             VALUES (?1, ?2, ?3, 'command.queued', 'desktop', 0, ?4, ?5)",
            params![device_id, command_id, target_revision, summary, now],
        )?;

        Ok(DeviceConfigCommand {
            id: command_id,
            base_revision,
            target_revision,
            mutation,
            summary,
            actor_type: "desktop".to_string(),
            actor_id: 0,
            status: "pending".to_string(),
            failure_reason: None,
            created_at: now.clone(),
            updated_at: now,
            applied_at: None,
        })
    }

    fn list_device_config_audit_logs(
        &self,
        device_id: i64,
        limit: i32,
        offset: i32,
    ) -> StoreResult<Paginated<DeviceConfigAuditLog>> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        let mut stmt = conn.prepare(
            "SELECT id, device_id, command_id, revision, event_type, actor_type, actor_id, summary, created_at
             FROM device_config_audit_logs
             WHERE device_id = ?1
             ORDER BY id DESC
             LIMIT ?2 OFFSET ?3",
        )?;
        let logs = stmt
            .query_map(params![device_id, limit, offset], |row| {
                Ok(DeviceConfigAuditLog {
                    id: row.get(0)?,
                    device_id: row.get(1)?,
                    command_id: row.get(2)?,
                    revision: row.get(3)?,
                    event_type: row.get(4)?,
                    actor_type: row.get(5)?,
                    actor_id: row.get(6)?,
                    summary: row.get(7)?,
                    created_at: row.get(8)?,
                })
            })?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(Paginated {
            items: logs,
            limit,
            offset,
        })
    }

    fn replace_device_config_pending_commands(
        &self,
        device_id: i64,
        commands: Vec<DeviceConfigCommand>,
    ) -> StoreResult<()> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        conn.execute(
            "DELETE FROM device_config_commands WHERE device_id = ?1 AND status = 'pending'",
            params![device_id],
        )?;
        for command in commands {
            conn.execute(
                "INSERT INTO device_config_commands (id, device_id, base_revision, target_revision, mutation, summary, actor_type, actor_id, status, failure_reason, created_at, updated_at, applied_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)",
                params![
                    command.id,
                    device_id,
                    command.base_revision,
                    command.target_revision,
                    serde_json::to_string(&command.mutation)?,
                    command.summary,
                    command.actor_type,
                    command.actor_id,
                    command.status,
                    command.failure_reason,
                    command.created_at,
                    command.updated_at,
                    command.applied_at,
                ],
            )?;
        }
        Ok(())
    }

    fn clear_device_config_pending_commands(&self, device_id: i64) -> StoreResult<()> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        conn.execute(
            "DELETE FROM device_config_commands WHERE device_id = ?1 AND status = 'pending'",
            params![device_id],
        )?;
        Ok(())
    }

    fn list_devices(&self) -> StoreResult<Vec<Device>> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
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

    fn patch_device(
        &self,
        device_id: i64,
        display_name: Option<&str>,
        enabled: Option<bool>,
    ) -> StoreResult<Value> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
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
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        conn.execute(
            "UPDATE devices SET revoked_at = datetime('now'), updated_at = datetime('now') WHERE id = ?1",
            params![device_id],
        )?;
        conn.execute(
            "UPDATE local_device_tokens SET revoked_at = datetime('now') WHERE device_id = ?1",
            params![device_id],
        )?;
        Ok(json!({ "ok": true }))
    }

    fn create_bind_code(&self) -> StoreResult<BindCode> {
        use uuid::Uuid;
        let code = Uuid::new_v4().to_string();
        let expires_at = chrono::Utc::now() + chrono::Duration::minutes(10);
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        conn.execute(
            "INSERT INTO local_device_bind_codes (code_hash, expires_at) VALUES (?1, ?2)",
            params![hash_secret(&code), expires_at.to_rfc3339()],
        )?;
        Ok(BindCode {
            code,
            expires_at: expires_at.to_rfc3339(),
        })
    }

    fn upsert_devices(&self, devices: Vec<Device>) -> StoreResult<()> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        conn.execute_batch("BEGIN")
            .map_err(|e| StoreError::Internal(e.to_string()))?;
        let result = (|| -> StoreResult<()> {
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
            let device_ids = devices.iter().map(|device| device.id).collect::<Vec<_>>();
            Self::seed_device_mirrors_from_legacy_snapshot(&conn, Some(&device_ids))?;
            Ok(())
        })();
        if result.is_ok() {
            conn.execute_batch("COMMIT")
                .map_err(|e| StoreError::Internal(e.to_string()))?;
        } else {
            conn.execute_batch("ROLLBACK")
                .map_err(|e| StoreError::Internal(e.to_string()))?;
        }
        result
    }

    fn list_records(&self, limit: i32, device_id: Option<i64>) -> StoreResult<Paginated<Record>> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
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
        let params_ref: Vec<&dyn rusqlite::types::ToSql> =
            param_values.iter().map(|p| p.as_ref()).collect();
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
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
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
            None => Err(StoreError::Internal(format!(
                "Record {} not found",
                record_id
            ))),
        }
    }

    fn upsert_records(&self, records: Vec<Record>) -> StoreResult<()> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
        conn.execute_batch("BEGIN")
            .map_err(|e| StoreError::Internal(e.to_string()))?;
        let result = (|| -> StoreResult<()> {
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
        })();
        if result.is_ok() {
            conn.execute_batch("COMMIT")
                .map_err(|e| StoreError::Internal(e.to_string()))?;
        } else {
            conn.execute_batch("ROLLBACK")
                .map_err(|e| StoreError::Internal(e.to_string()))?;
        }
        result
    }

    fn get_system_info(&self) -> StoreResult<SystemInfo> {
        let conn = self
            .conn
            .lock()
            .map_err(|_| StoreError::Internal("sqlite mutex poisoned".to_string()))?;
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
    fn device_config_reads_latest_local_mirror() {
        let store = test_store();
        store
            .upsert_device_config_mirror(
                1,
                1,
                json!({"senders": []}),
                Some("2026-01-01T00:00:00Z".to_string()),
            )
            .unwrap();

        let state = store.get_device_config(1).unwrap().unwrap();
        assert_eq!(state.device_id, 1);
        assert_eq!(state.revision, 1);
        assert_eq!(state.snapshot, json!({"senders": []}));
        assert!(state.pending_commands.is_empty());
    }

    #[test]
    fn device_config_queue_and_audit_logs_are_recorded() {
        let store = test_store();
        store
            .upsert_device_config_mirror(
                1,
                1,
                json!({"senders": []}),
                Some("2026-01-01T00:00:00Z".to_string()),
            )
            .unwrap();

        let command = store
            .queue_device_config_command(
                1,
                1,
                "senders:update".to_string(),
                json!({"operations":[{"type":"replace_senders","senders":[{"id":1,"type":4,"name":"alpha","jsonSetting":"{}","status":1,"receiveCode":1,"receiveNonCode":1,"receiveAppNotify":1,"receiveCallNotify":0}]}]}),
            )
            .unwrap();

        assert_eq!(command.base_revision, 1);
        assert_eq!(command.target_revision, 2);
        assert_eq!(command.status, "pending");

        let state = store.get_device_config(1).unwrap().unwrap();
        assert_eq!(state.pending_commands.len(), 1);
        assert_eq!(state.pending_commands[0].summary, "senders:update");

        let logs = store.list_device_config_audit_logs(1, 20, 0).unwrap();
        assert_eq!(logs.items.len(), 1);
        assert_eq!(logs.items[0].event_type, "command.queued");
    }

    #[test]
    fn device_config_queue_uses_pending_target_revision_and_isolates_devices() {
        let store = test_store();
        store
            .upsert_device_config_mirror(
                1,
                1,
                json!({"senders": [{"id": 1}]}),
                Some("2026-01-01T00:00:00Z".to_string()),
            )
            .unwrap();
        store
            .upsert_device_config_mirror(
                2,
                4,
                json!({"senders": [{"id": 2}]}),
                Some("2026-01-01T00:00:00Z".to_string()),
            )
            .unwrap();

        let first = store
            .queue_device_config_command(
                1,
                1,
                "senders:first".to_string(),
                json!({"operations":[{"type":"replace_senders","senders":[{"id":10}]}]}),
            )
            .unwrap();
        let second = store
            .queue_device_config_command(
                1,
                first.target_revision,
                "senders:second".to_string(),
                json!({"operations":[{"type":"replace_senders","senders":[{"id":11}]}]}),
            )
            .unwrap();

        assert_eq!(first.base_revision, 1);
        assert_eq!(first.target_revision, 2);
        assert_eq!(second.base_revision, 2);
        assert_eq!(second.target_revision, 3);

        let first_state = store.get_device_config(1).unwrap().unwrap();
        assert_eq!(first_state.revision, 1);
        assert_eq!(first_state.pending_commands.len(), 2);
        assert_eq!(first_state.pending_commands[0].summary, "senders:first");
        assert_eq!(first_state.pending_commands[1].summary, "senders:second");

        let second_state = store.get_device_config(2).unwrap().unwrap();
        assert_eq!(second_state.revision, 4);
        assert_eq!(second_state.snapshot, json!({"senders": [{"id": 2}]}));
        assert!(second_state.pending_commands.is_empty());
    }

    #[test]
    fn legacy_config_snapshot_is_seeded_into_device_mirrors() {
        let store = test_store();
        {
            let conn = store.conn.lock().unwrap();
            conn.execute_batch(
                "
                CREATE TABLE config_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    revision INTEGER NOT NULL,
                    content TEXT NOT NULL DEFAULT '{}',
                    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                );
                INSERT INTO devices (id, user_id, device_name, device_model, platform, app_version, display_name, enabled, local_addresses, capabilities, created_at, updated_at)
                VALUES (7, 0, 'Phone', 'Pixel', 'android', '1.0', 'Phone', 1, '[]', '{}', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z');
                INSERT INTO config_snapshots (revision, content, updated_at)
                VALUES (5, '{\"senders\":[{\"id\":1}]}', '2026-01-01T00:00:00Z');
                ",
            ).unwrap();
            SqliteStore::seed_device_mirrors_from_legacy_snapshot(&conn, None).unwrap();
        }

        let state = store.get_device_config(7).unwrap().unwrap();
        assert_eq!(state.revision, 5);
        assert_eq!(state.snapshot, json!({"senders":[{"id":1}]}));
    }

    #[test]
    fn legacy_config_snapshot_is_seeded_for_each_existing_device() {
        let store = test_store();
        {
            let conn = store.conn.lock().unwrap();
            conn.execute_batch(
                "
                CREATE TABLE config_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    revision INTEGER NOT NULL,
                    content TEXT NOT NULL DEFAULT '{}',
                    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
                );
                INSERT INTO devices (id, user_id, device_name, device_model, platform, app_version, display_name, enabled, local_addresses, capabilities, created_at, updated_at)
                VALUES
                  (7, 0, 'Phone', 'Pixel', 'android', '1.0', 'Phone', 1, '[]', '{}', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'),
                  (8, 0, 'Tablet', 'Pixel', 'android', '1.0', 'Tablet', 1, '[]', '{}', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z');
                INSERT INTO config_snapshots (revision, content, updated_at)
                VALUES (5, '{\"senders\":[{\"id\":1}]}', '2026-01-01T00:00:00Z');
                ",
            ).unwrap();
            SqliteStore::seed_device_mirrors_from_legacy_snapshot(&conn, None).unwrap();
        }

        let phone = store.get_device_config(7).unwrap().unwrap();
        let tablet = store.get_device_config(8).unwrap().unwrap();
        assert_eq!(phone.revision, 5);
        assert_eq!(tablet.revision, 5);
        assert_eq!(phone.snapshot, json!({"senders":[{"id":1}]}));
        assert_eq!(tablet.snapshot, json!({"senders":[{"id":1}]}));
    }

    #[test]
    fn devices_empty_by_default() {
        let store = test_store();
        assert!(store.list_devices().unwrap().is_empty());
    }

    #[test]
    fn local_bind_code_is_single_use_and_device_token_is_hashed() {
        let store = test_store();
        let bind = store.create_bind_code().unwrap();
        let registered = store
            .register_local_device(
                &bind.code,
                "Phone",
                "Pixel",
                "android",
                "1.0",
                "secret-device-token",
            )
            .unwrap()
            .unwrap();
        assert_eq!(
            store
                .authenticate_local_device("secret-device-token")
                .unwrap(),
            Some(registered.id),
        );
        assert_eq!(store.authenticate_local_device("wrong").unwrap(), None);
        assert!(store
            .register_local_device(
                &bind.code,
                "Second",
                "Pixel",
                "android",
                "1.0",
                "second-token",
            )
            .unwrap()
            .is_none(),);

        let conn = store.conn.lock().unwrap();
        let stored_hash: String = conn
            .query_row(
                "SELECT token_hash FROM local_device_tokens WHERE device_id=?1",
                params![registered.id],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(stored_hash, hash_secret("secret-device-token"));
        assert_ne!(stored_hash, "secret-device-token");
    }

    #[test]
    fn devices_upsert_and_list() {
        let store = test_store();
        let devices = vec![
            Device {
                id: 1,
                user_id: 0,
                device_name: "Phone".to_string(),
                device_model: "Pixel".to_string(),
                platform: "android".to_string(),
                app_version: "1.0".to_string(),
                display_name: "My Phone".to_string(),
                enabled: true,
                revoked_at: None,
                last_seen_at: None,
                local_addresses: json!([]),
                capabilities: json!({}),
                created_at: "2026-01-01T00:00:00Z".to_string(),
                updated_at: "2026-01-01T00:00:00Z".to_string(),
            },
            Device {
                id: 2,
                user_id: 0,
                device_name: "Tablet".to_string(),
                device_model: "iPad".to_string(),
                platform: "ios".to_string(),
                app_version: "1.0".to_string(),
                display_name: "My Tablet".to_string(),
                enabled: true,
                revoked_at: None,
                last_seen_at: None,
                local_addresses: json!([]),
                capabilities: json!({}),
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
            id: 1,
            user_id: 0,
            device_name: "Phone".to_string(),
            device_model: "Pixel".to_string(),
            platform: "android".to_string(),
            app_version: "1.0".to_string(),
            display_name: "Old Name".to_string(),
            enabled: true,
            revoked_at: None,
            last_seen_at: None,
            local_addresses: json!([]),
            capabilities: json!({}),
            created_at: "2026-01-01T00:00:00Z".to_string(),
            updated_at: "2026-01-01T00:00:00Z".to_string(),
        };
        store.upsert_devices(vec![device]).unwrap();

        let updated = Device {
            id: 1,
            user_id: 0,
            device_name: "Phone".to_string(),
            device_model: "Pixel".to_string(),
            platform: "android".to_string(),
            app_version: "1.0".to_string(),
            display_name: "New Name".to_string(),
            enabled: false,
            revoked_at: None,
            last_seen_at: None,
            local_addresses: json!([]),
            capabilities: json!({}),
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
        let records = vec![Record {
            id: 1,
            device_id: 1,
            event_id: Some("evt1".to_string()),
            record_type: "sms".to_string(),
            sender: "Bank".to_string(),
            body: "Code 123456".to_string(),
            sms_code: "123456".to_string(),
            package_name: "com.sms".to_string(),
            metadata: json!({}),
            msg_type: 0,
            call_type: 0,
            occurred_at: "2026-01-01T00:00:00Z".to_string(),
            uploaded_at: "2026-01-01T00:00:00Z".to_string(),
        }];
        store.upsert_records(records).unwrap();
        let result = store.list_records(10, None).unwrap();
        assert_eq!(result.items.len(), 1);
        assert_eq!(result.items[0].sms_code, "123456");
    }

    #[test]
    fn records_upsert_updates_existing() {
        let store = test_store();
        let record = Record {
            id: 1,
            device_id: 1,
            event_id: Some("evt1".to_string()),
            record_type: "sms".to_string(),
            sender: "Bank".to_string(),
            body: "Old body".to_string(),
            sms_code: "111".to_string(),
            package_name: "com.sms".to_string(),
            metadata: json!({}),
            msg_type: 0,
            call_type: 0,
            occurred_at: "2026-01-01T00:00:00Z".to_string(),
            uploaded_at: "2026-01-01T00:00:00Z".to_string(),
        };
        store.upsert_records(vec![record]).unwrap();

        let updated = Record {
            id: 1,
            device_id: 1,
            event_id: Some("evt1".to_string()),
            record_type: "sms".to_string(),
            sender: "Bank".to_string(),
            body: "New body".to_string(),
            sms_code: "222".to_string(),
            package_name: "com.sms".to_string(),
            metadata: json!({}),
            msg_type: 0,
            call_type: 0,
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
    fn record_snapshot_preserves_ids_and_reports_insert_update_delete_counts() {
        let store = test_store();
        let first = snapshot_record("evt-1", "first");
        let initial = store
            .sync_local_device_records(7, &[first.clone()], true)
            .unwrap();
        assert_eq!(initial.inserted, 1);
        assert_eq!(initial.updated, 0);
        assert_eq!(initial.deleted, 0);
        let stable_id = store.list_records(10, None).unwrap().items[0].id;

        let second = snapshot_record("evt-2", "second");
        let next = store
            .sync_local_device_records(
                7,
                &[snapshot_record("evt-1", "updated"), second.clone()],
                true,
            )
            .unwrap();
        assert_eq!(next.inserted, 1);
        assert_eq!(next.updated, 1);
        assert_eq!(next.deleted, 0);
        let records = store.list_records(10, None).unwrap();
        assert_eq!(
            records
                .items
                .iter()
                .find(|record| record.event_id.as_deref() == Some("evt-1"))
                .unwrap()
                .id,
            stable_id,
        );

        let final_result = store.sync_local_device_records(7, &[second], true).unwrap();
        assert_eq!(final_result.inserted, 0);
        assert_eq!(final_result.updated, 1);
        assert_eq!(final_result.deleted, 1);
    }

    fn snapshot_record(event_id: &str, body: &str) -> Record {
        Record {
            id: 0,
            device_id: 7,
            event_id: Some(event_id.to_string()),
            record_type: "sms".to_string(),
            sender: "Bank".to_string(),
            body: body.to_string(),
            sms_code: String::new(),
            package_name: "com.sms".to_string(),
            metadata: json!({}),
            msg_type: 0,
            call_type: 0,
            occurred_at: "2026-01-01T00:00:00Z".to_string(),
            uploaded_at: "2026-01-01T00:00:00Z".to_string(),
        }
    }

    #[test]
    fn system_info_reports_device_count() {
        let store = test_store();
        let info = store.get_system_info().unwrap();
        assert_eq!(info.user_count, 0);

        let device = Device {
            id: 1,
            user_id: 0,
            device_name: "Phone".to_string(),
            device_model: "".to_string(),
            platform: "android".to_string(),
            app_version: "".to_string(),
            display_name: "".to_string(),
            enabled: true,
            revoked_at: None,
            last_seen_at: None,
            local_addresses: json!([]),
            capabilities: json!({}),
            created_at: "2026-01-01T00:00:00Z".to_string(),
            updated_at: "2026-01-01T00:00:00Z".to_string(),
        };
        store.upsert_devices(vec![device]).unwrap();

        let info = store.get_system_info().unwrap();
        assert_eq!(info.user_count, 1);
    }
}
