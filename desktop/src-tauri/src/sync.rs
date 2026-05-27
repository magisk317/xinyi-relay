use serde::{Deserialize, Serialize};

use super::store::{Store, StoreError, StoreResult};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum SyncResult {
    UpToDate,
    Pulled { new_revision: i64 },
    Pushed { new_revision: i64 },
    Conflict { local_revision: i64, remote_revision: i64 },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncReport {
    pub config: SyncResult,
    pub devices_synced: usize,
    pub records_synced: usize,
    pub error: Option<String>,
}

fn parse_timestamp(s: &Option<String>) -> Option<chrono::DateTime<chrono::Utc>> {
    s.as_ref().and_then(|ts| chrono::DateTime::parse_from_rfc3339(ts).ok().map(|dt| dt.with_timezone(&chrono::Utc)))
}

pub fn sync_pull(
    local: &dyn Store,
    remote: &dyn Store,
) -> StoreResult<SyncReport> {
    let remote_config = remote.get_config_snapshot()?;
    let local_config = local.get_config_snapshot()?;

    let config_result = match (remote_config, local_config) {
        (Some(remote_snap), Some(local_snap)) => {
            if remote_snap.revision > local_snap.revision {
                let new_rev = remote_snap.revision;
                local.put_config_snapshot(local_snap.revision, remote_snap.snapshot)?;
                SyncResult::Pulled { new_revision: new_rev }
            } else if local_snap.revision > remote_snap.revision {
                SyncResult::Conflict {
                    local_revision: local_snap.revision,
                    remote_revision: remote_snap.revision,
                }
            } else {
                // Same revision — use updated_at for LWW tiebreak
                let remote_ts = parse_timestamp(&remote_snap.updated_at);
                let local_ts = parse_timestamp(&local_snap.updated_at);
                match (remote_ts, local_ts) {
                    (Some(r), Some(l)) if r > l => {
                        local.put_config_snapshot(local_snap.revision, remote_snap.snapshot)?;
                        SyncResult::Pulled { new_revision: local_snap.revision }
                    }
                    _ => SyncResult::UpToDate,
                }
            }
        }
        (Some(remote_snap), None) => {
            let new_rev = remote_snap.revision;
            local.put_config_snapshot(0, remote_snap.snapshot)?;
            SyncResult::Pulled { new_revision: new_rev }
        }
        (None, Some(_)) => {
            // Local has config but remote doesn't — push local config
            SyncResult::Conflict {
                local_revision: local.get_config_snapshot()?.map(|s| s.revision).unwrap_or(0),
                remote_revision: 0,
            }
        }
        (None, None) => SyncResult::UpToDate,
    };

    // Sync devices from remote
    let remote_devices = remote.list_devices()?;
    let devices_synced = remote_devices.len();
    if devices_synced > 0 {
        local.upsert_devices(remote_devices)?;
    }

    // Sync records from remote
    let remote_records = remote.list_records(100, None)?;
    let records_synced = remote_records.items.len();
    if records_synced > 0 {
        local.upsert_records(remote_records.items)?;
    }

    Ok(SyncReport {
        config: config_result,
        devices_synced,
        records_synced,
        error: None,
    })
}

pub fn sync_push(
    local: &dyn Store,
    remote: &dyn Store,
) -> StoreResult<SyncReport> {
    let local_config = local.get_config_snapshot()?;
    let remote_config = remote.get_config_snapshot()?;

    let config_result = match (local_config, remote_config) {
        (Some(local_snap), Some(remote_snap)) => {
            if local_snap.revision > remote_snap.revision {
                let result = remote.put_config_snapshot(remote_snap.revision, local_snap.snapshot);
                match result {
                    Ok(new_snap) => SyncResult::Pushed { new_revision: new_snap.revision },
                    Err(StoreError::Conflict { local: _, remote }) => SyncResult::Conflict {
                        local_revision: local_snap.revision,
                        remote_revision: remote,
                    },
                    Err(e) => return Err(e),
                }
            } else if remote_snap.revision > local_snap.revision {
                SyncResult::Conflict {
                    local_revision: local_snap.revision,
                    remote_revision: remote_snap.revision,
                }
            } else {
                // Same revision — use updated_at for LWW tiebreak
                let local_ts = parse_timestamp(&local_snap.updated_at);
                let remote_ts = parse_timestamp(&remote_snap.updated_at);
                match (local_ts, remote_ts) {
                    (Some(l), Some(r)) if l > r => {
                        let result = remote.put_config_snapshot(remote_snap.revision, local_snap.snapshot);
                        match result {
                            Ok(new_snap) => SyncResult::Pushed { new_revision: new_snap.revision },
                            Err(StoreError::Conflict { local: _, remote }) => SyncResult::Conflict {
                                local_revision: local_snap.revision,
                                remote_revision: remote,
                            },
                            Err(e) => return Err(e),
                        }
                    }
                    _ => SyncResult::UpToDate,
                }
            }
        }
        (Some(local_snap), None) => {
            let new_snap = remote.put_config_snapshot(0, local_snap.snapshot)?;
            SyncResult::Pushed { new_revision: new_snap.revision }
        }
        (None, Some(_)) => SyncResult::UpToDate,
        (None, None) => SyncResult::UpToDate,
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
pub fn sync_initial_pull(
    local: &dyn Store,
    remote: &dyn Store,
) -> StoreResult<SyncReport> {
    // Always pull config
    let remote_config = remote.get_config_snapshot()?;
    let config_result = match remote_config {
        Some(remote_snap) => {
            let local_config = local.get_config_snapshot()?;
            let local_rev = local_config.as_ref().map(|s| s.revision).unwrap_or(0);
            if remote_snap.revision > local_rev {
                local.put_config_snapshot(local_rev, remote_snap.snapshot)?;
                SyncResult::Pulled { new_revision: remote_snap.revision }
            } else {
                SyncResult::UpToDate
            }
        }
        None => SyncResult::UpToDate,
    };

    // Pull all devices
    let remote_devices = remote.list_devices()?;
    let devices_synced = remote_devices.len();
    if devices_synced > 0 {
        local.upsert_devices(remote_devices)?;
    }

    // Pull recent records
    let remote_records = remote.list_records(100, None)?;
    let records_synced = remote_records.items.len();
    if records_synced > 0 {
        local.upsert_records(remote_records.items)?;
    }

    Ok(SyncReport {
        config: config_result,
        devices_synced,
        records_synced,
        error: None,
    })
}
