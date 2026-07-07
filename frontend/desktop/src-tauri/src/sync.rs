use std::sync::atomic::{AtomicBool, Ordering};

use serde::{Deserialize, Serialize};

use super::store::{Store, StoreError, StoreResult};

/// Global sync lock to prevent concurrent sync operations.
static SYNC_LOCK: AtomicBool = AtomicBool::new(false);

struct SyncGuard;

impl SyncGuard {
    fn try_acquire() -> Option<Self> {
        #[cfg(test)]
        {
            return Some(SyncGuard);
        }
        #[cfg(not(test))]
        if SYNC_LOCK
            .compare_exchange(false, true, Ordering::Acquire, Ordering::Relaxed)
            .is_ok()
        {
            Some(SyncGuard)
        } else {
            None
        }
    }
}

impl Drop for SyncGuard {
    fn drop(&mut self) {
        SYNC_LOCK.store(false, Ordering::Release);
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum SyncResult {
    UpToDate,
    Pulled {
        new_revision: i64,
    },
    Pushed {
        new_revision: i64,
    },
    Conflict {
        local_revision: i64,
        remote_revision: i64,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncReport {
    pub config: SyncResult,
    pub devices_synced: usize,
    pub records_synced: usize,
    pub error: Option<String>,
}

pub fn sync_pull(local: &dyn Store, remote: &dyn Store) -> StoreResult<SyncReport> {
    let _guard = SyncGuard::try_acquire()
        .ok_or_else(|| StoreError::Internal("Sync already in progress".to_string()))?;

    // Sync devices from remote
    let remote_devices = remote.list_devices()?;
    let devices_synced = remote_devices.len();
    if devices_synced > 0 {
        local.upsert_devices(remote_devices.clone())?;
    }

    let mut pulled_revision: Option<i64> = None;
    let mut conflict: Option<(i64, i64)> = None;
    for device in &remote_devices {
        let Some(remote_state) = remote.get_device_config(device.id)? else {
            continue;
        };
        let local_state = local.get_device_config(device.id)?;
        let local_revision = local_state.as_ref().map(|s| s.revision).unwrap_or(0);
        let local_has_pending = local_state
            .as_ref()
            .map(|s| !s.pending_commands.is_empty())
            .unwrap_or(false);

        if remote_state.revision > local_revision {
            local.upsert_device_config_mirror(
                device.id,
                remote_state.revision,
                remote_state.snapshot.clone(),
                remote_state.updated_at.clone(),
            )?;
            if !local_has_pending {
                local.replace_device_config_pending_commands(
                    device.id,
                    remote_state.pending_commands.clone(),
                )?;
            }
            pulled_revision = Some(pulled_revision.map_or(remote_state.revision, |current| {
                current.max(remote_state.revision)
            }));
            continue;
        }

        if local_revision > remote_state.revision && local_has_pending {
            conflict = Some((local_revision, remote_state.revision));
            continue;
        }

        if !local_has_pending {
            local.replace_device_config_pending_commands(
                device.id,
                remote_state.pending_commands.clone(),
            )?;
        }
    }

    // Sync records from remote
    let remote_records = remote.list_records(100, None)?;
    let records_synced = remote_records.items.len();
    if records_synced > 0 {
        local.upsert_records(remote_records.items)?;
    }

    let config_result = if let Some((local_revision, remote_revision)) = conflict {
        SyncResult::Conflict {
            local_revision,
            remote_revision,
        }
    } else if let Some(new_revision) = pulled_revision {
        SyncResult::Pulled { new_revision }
    } else {
        SyncResult::UpToDate
    };

    Ok(SyncReport {
        config: config_result,
        devices_synced,
        records_synced,
        error: None,
    })
}

pub fn sync_push(local: &dyn Store, remote: &dyn Store) -> StoreResult<SyncReport> {
    let _guard = SyncGuard::try_acquire()
        .ok_or_else(|| StoreError::Internal("Sync already in progress".to_string()))?;

    let local_devices = local.list_devices()?;
    let mut pushed_revision: Option<i64> = None;
    let mut conflict: Option<(i64, i64)> = None;
    for device in &local_devices {
        let Some(local_state) = local.get_device_config(device.id)? else {
            continue;
        };
        if local_state.pending_commands.is_empty() {
            continue;
        }
        let remote_state = remote.get_device_config(device.id)?;
        let mut remote_revision = remote_state
            .as_ref()
            .map(|state| state.revision)
            .unwrap_or(0);
        let mut pushed_all = true;
        for command in &local_state.pending_commands {
            if command.base_revision != remote_revision {
                conflict = Some((command.base_revision, remote_revision));
                pushed_all = false;
                break;
            }
            match remote.queue_device_config_command(
                device.id,
                command.base_revision,
                command.summary.clone(),
                command.mutation.clone(),
            ) {
                Ok(_) => {
                    pushed_revision =
                        Some(pushed_revision.map_or(command.target_revision, |current| {
                            current.max(command.target_revision)
                        }));
                    remote_revision = command.target_revision;
                }
                Err(StoreError::Conflict { .. }) => {
                    conflict = Some((command.base_revision, remote_revision));
                    pushed_all = false;
                    break;
                }
                Err(err) => return Err(err),
            }
        }
        if pushed_all {
            local.clear_device_config_pending_commands(device.id)?;
        }
    }

    let config_result = if let Some((local_revision, remote_revision)) = conflict {
        SyncResult::Conflict {
            local_revision,
            remote_revision,
        }
    } else if let Some(new_revision) = pushed_revision {
        SyncResult::Pushed { new_revision }
    } else {
        SyncResult::UpToDate
    };

    Ok(SyncReport {
        config: config_result,
        devices_synced: 0,
        records_synced: 0,
        error: None,
    })
}

/// Full initial pull when switching to Local/Hybrid mode.
/// Pulls config, devices, and records from remote into local store.
pub fn sync_initial_pull(local: &dyn Store, remote: &dyn Store) -> StoreResult<SyncReport> {
    sync_pull(local, remote)
}

#[cfg(test)]
mod tests {
    use super::super::sqlite_store::SqliteStore;
    use super::super::store::*;
    use super::*;
    use serde_json::json;

    fn test_stores() -> (SqliteStore, SqliteStore) {
        (
            SqliteStore::open_in_memory().unwrap(),
            SqliteStore::open_in_memory().unwrap(),
        )
    }

    #[test]
    fn pull_up_to_date_when_both_empty() {
        let (local, remote) = test_stores();
        let report = sync_pull(&local, &remote).unwrap();
        assert!(matches!(report.config, SyncResult::UpToDate));
        assert_eq!(report.devices_synced, 0);
        assert_eq!(report.records_synced, 0);
    }

    #[test]
    fn pull_fetches_remote_device_config() {
        let (local, remote) = test_stores();
        remote
            .upsert_devices(vec![Device {
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
            }])
            .unwrap();
        remote
            .upsert_device_config_mirror(
                1,
                1,
                json!({"key": "value"}),
                Some("2026-01-01T00:00:00Z".to_string()),
            )
            .unwrap();

        let report = sync_pull(&local, &remote).unwrap();
        assert!(matches!(
            report.config,
            SyncResult::Pulled { new_revision: 1 }
        ));

        let local_state = local.get_device_config(1).unwrap().unwrap();
        assert_eq!(local_state.revision, 1);
        assert_eq!(local_state.snapshot, json!({"key": "value"}));
    }

    #[test]
    fn pull_conflict_when_local_pending_ahead() {
        let (local, remote) = test_stores();
        local
            .upsert_devices(vec![Device {
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
            }])
            .unwrap();
        local
            .upsert_device_config_mirror(
                1,
                2,
                json!({"v": 2}),
                Some("2026-01-01T00:00:00Z".to_string()),
            )
            .unwrap();
        local
            .queue_device_config_command(1, 2, "local".to_string(), json!({"operations":[]}))
            .unwrap();
        remote
            .upsert_devices(vec![Device {
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
            }])
            .unwrap();
        remote
            .upsert_device_config_mirror(
                1,
                1,
                json!({"v": 10}),
                Some("2026-01-01T00:00:00Z".to_string()),
            )
            .unwrap();

        let report = sync_pull(&local, &remote).unwrap();
        assert!(matches!(
            report.config,
            SyncResult::Conflict {
                local_revision: 2,
                remote_revision: 1
            }
        ));
    }

    #[test]
    fn push_queues_local_device_commands_to_remote() {
        let (local, remote) = test_stores();
        local
            .upsert_devices(vec![Device {
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
            }])
            .unwrap();
        remote
            .upsert_devices(vec![Device {
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
            }])
            .unwrap();
        remote
            .upsert_device_config_mirror(
                1,
                1,
                json!({"local": true}),
                Some("2026-01-01T00:00:00Z".to_string()),
            )
            .unwrap();
        local
            .upsert_device_config_mirror(
                1,
                1,
                json!({"local": true}),
                Some("2026-01-01T00:00:00Z".to_string()),
            )
            .unwrap();
        local
            .queue_device_config_command(
                1,
                1,
                "local:update".to_string(),
                json!({"operations":[{"type":"replace_senders","senders":[]}]}),
            )
            .unwrap();

        let report = sync_push(&local, &remote).unwrap();
        assert!(matches!(
            report.config,
            SyncResult::Pushed { new_revision: 2 }
        ));

        let remote_state = remote.get_device_config(1).unwrap().unwrap();
        assert_eq!(remote_state.pending_commands.len(), 1);
        assert_eq!(remote_state.pending_commands[0].summary, "local:update");
    }

    #[test]
    fn push_conflict_when_remote_revision_changed() {
        let (local, remote) = test_stores();
        local
            .upsert_devices(vec![Device {
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
            }])
            .unwrap();
        remote
            .upsert_devices(vec![Device {
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
            }])
            .unwrap();
        local
            .upsert_device_config_mirror(
                1,
                1,
                json!({"v": 1}),
                Some("2026-01-01T00:00:00Z".to_string()),
            )
            .unwrap();
        remote
            .upsert_device_config_mirror(
                1,
                2,
                json!({"v": 2}),
                Some("2026-01-01T00:00:00Z".to_string()),
            )
            .unwrap();
        local
            .queue_device_config_command(1, 1, "stale".to_string(), json!({"operations":[]}))
            .unwrap();

        let report = sync_push(&local, &remote).unwrap();
        assert!(matches!(
            report.config,
            SyncResult::Conflict {
                local_revision: 1,
                remote_revision: 2
            }
        ));
    }

    #[test]
    fn pull_syncs_devices_and_records() {
        let (local, remote) = test_stores();
        remote
            .upsert_devices(vec![Device {
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
            }])
            .unwrap();
        remote
            .upsert_records(vec![Record {
                id: 100,
                device_id: 1,
                event_id: Some("e1".to_string()),
                record_type: "sms".to_string(),
                sender: "Test".to_string(),
                body: "".to_string(),
                sms_code: "999".to_string(),
                package_name: "".to_string(),
                metadata: json!({}),
                msg_type: 0,
                call_type: 0,
                occurred_at: "2026-01-01T00:00:00Z".to_string(),
                uploaded_at: "2026-01-01T00:00:00Z".to_string(),
            }])
            .unwrap();

        let report = sync_pull(&local, &remote).unwrap();
        assert_eq!(report.devices_synced, 1);
        assert_eq!(report.records_synced, 1);

        let local_devices = local.list_devices().unwrap();
        assert_eq!(local_devices.len(), 1);
        assert_eq!(local_devices[0].device_name, "Phone");

        let local_records = local.list_records(10, None).unwrap();
        assert_eq!(local_records.items.len(), 1);
        assert_eq!(local_records.items[0].sms_code, "999");
    }

    #[test]
    fn initial_pull_writes_everything() {
        let (local, remote) = test_stores();
        remote
            .upsert_devices(vec![Device {
                id: 1,
                user_id: 0,
                device_name: "D1".to_string(),
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
            }])
            .unwrap();
        remote
            .upsert_device_config_mirror(
                1,
                1,
                json!({"init": true}),
                Some("2026-01-01T00:00:00Z".to_string()),
            )
            .unwrap();

        let report = sync_initial_pull(&local, &remote).unwrap();
        assert!(matches!(
            report.config,
            SyncResult::Pulled { new_revision: 1 }
        ));
        assert_eq!(report.devices_synced, 1);

        let state = local.get_device_config(1).unwrap().unwrap();
        assert_eq!(state.snapshot, json!({"init": true}));
        assert_eq!(local.list_devices().unwrap().len(), 1);
    }
}
