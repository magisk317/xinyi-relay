use super::*;
#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

pub(crate) fn persisted_state_path(app: &AppHandle) -> Result<PathBuf, String> {
    let dir = ensure_directory(app.path().app_config_dir().map_err(|err| err.to_string())?)?;
    Ok(dir.join("desktop-state.json"))
}

fn session_cache_dir_path(app: &AppHandle) -> Result<PathBuf, String> {
    let dir = ensure_directory(app.path().app_config_dir().map_err(|err| err.to_string())?)?;
    ensure_directory(dir.join("sessions"))
}

fn session_cache_path(app: &AppHandle, profile_id: &str) -> Result<PathBuf, String> {
    Ok(session_cache_dir_path(app)?.join(format!("profile-{}.json", profile_id)))
}

pub(crate) fn ensure_directory(path: PathBuf) -> Result<PathBuf, String> {
    fs::create_dir_all(&path).map_err(|err| err.to_string())?;
    Ok(path)
}

pub(crate) fn load_persisted_state(app: &AppHandle) -> Result<DesktopPersistedState, String> {
    let path = persisted_state_path(app)?;
    if !path.exists() {
        let default = DesktopPersistedState::default();
        persist_state_to_disk(app, &default)?;
        return Ok(default);
    }

    let content = fs::read_to_string(path).map_err(|err| err.to_string())?;
    let mut persisted: DesktopPersistedState =
        serde_json::from_str(&content).map_err(|err| err.to_string())?;
    let mut migrated = false;
    for profile in &mut persisted.profiles {
        if profile.allow_self_signed || profile.trusted_fingerprint.is_some() {
            profile.allow_self_signed = false;
            profile.trusted_fingerprint = None;
            profile.trusted_issuer = Some("System certificate trust".to_string());
            migrated = true;
        }
    }
    if migrated {
        persist_state_to_disk(app, &persisted)?;
    }
    Ok(persisted)
}

pub(crate) fn persist_state_to_disk(
    app: &AppHandle,
    persisted: &DesktopPersistedState,
) -> Result<(), String> {
    let path = persisted_state_path(app)?;
    let content = serde_json::to_string_pretty(persisted).map_err(|err| err.to_string())?;
    fs::write(path, content).map_err(|err| err.to_string())
}

pub(crate) fn active_profile(persisted: &DesktopPersistedState) -> Option<DesktopProfile> {
    persisted
        .profiles
        .iter()
        .find(|item| item.active)
        .cloned()
        .or_else(|| persisted.profiles.first().cloned())
}

pub(crate) fn current_active_profile(
    state: &DesktopAppState,
) -> Result<Option<DesktopProfile>, String> {
    let persisted = state
        .persisted
        .lock()
        .map_err(|_| "persisted state poisoned".to_string())?
        .clone();
    Ok(active_profile(&persisted))
}

fn session_entry(profile_id: &str) -> Result<Entry, String> {
    Entry::new(SERVICE_NAME, &format!("profile:{profile_id}")).map_err(|err| err.to_string())
}

pub(crate) fn load_session_for_profile(
    app: &AppHandle,
    profile_id: &str,
) -> Result<Option<DesktopSessionSecrets>, String> {
    let entry = match session_entry(profile_id) {
        Ok(entry) => entry,
        Err(error) => {
            eprintln!(
                "desktop session keyring initialization failed for profile={}, falling back to file: {}",
                profile_id, error
            );
            return load_session_from_file(app, profile_id);
        }
    };
    match entry.get_password() {
        Ok(value) => decode_protected_session_or_fallback(
            &value,
            || load_session_from_file(app, profile_id),
            || delete_session_file(app, profile_id),
        ),
        Err(KeyringError::NoEntry) => migrate_legacy_session_file(app, profile_id, &entry),
        Err(err) => {
            eprintln!(
                "desktop session keyring load failed for profile={}, falling back to file: {}",
                profile_id, err
            );
            load_session_from_file(app, profile_id)
        }
    }
}

fn decode_protected_session_or_fallback(
    value: &str,
    load_legacy: impl FnOnce() -> Result<Option<DesktopSessionSecrets>, String>,
    delete_legacy: impl FnOnce() -> Result<(), String>,
) -> Result<Option<DesktopSessionSecrets>, String> {
    match serde_json::from_str(value) {
        Ok(session) => {
            // Older builds wrote a file fallback even when the keyring worked.
            let _ = delete_legacy();
            Ok(Some(session))
        }
        Err(error) => {
            eprintln!("protected desktop session is invalid, falling back to file: {error}");
            load_legacy()
        }
    }
}

pub(crate) fn save_session_for_profile(
    app: &AppHandle,
    session: &DesktopSessionSecrets,
) -> Result<(), String> {
    let payload = serde_json::to_string(session).map_err(|err| err.to_string())?;
    let keyring_result = session_entry(&session.profile_id)
        .and_then(|entry| entry.set_password(&payload).map_err(|err| err.to_string()));
    finish_session_write(
        keyring_result,
        || save_session_to_file(app, session),
        || delete_session_file(app, &session.profile_id),
    )
}

pub(crate) fn delete_session_for_profile(app: &AppHandle, profile_id: &str) -> Result<(), String> {
    let entry = session_entry(profile_id)?;
    let keyring_result = match entry.delete_credential() {
        Ok(_) | Err(KeyringError::NoEntry) => Ok(()),
        Err(err) => Err(err.to_string()),
    };
    let file_result = delete_session_file(app, profile_id);

    match (keyring_result, file_result) {
        (Ok(_), Ok(_)) => Ok(()),
        (Ok(_), Err(err)) => {
            eprintln!(
                "desktop session file delete failed for profile={}, keyring delete succeeded: {}",
                profile_id, err
            );
            Ok(())
        }
        (Err(err), Ok(_)) => {
            eprintln!(
                "desktop session keyring delete failed for profile={}, file delete succeeded: {}",
                profile_id, err
            );
            Ok(())
        }
        (Err(keyring_err), Err(file_err)) => Err(format!(
            "failed to delete desktop session (keyring: {}; file: {})",
            keyring_err, file_err
        )),
    }
}

fn migrate_legacy_session_file(
    app: &AppHandle,
    profile_id: &str,
    entry: &Entry,
) -> Result<Option<DesktopSessionSecrets>, String> {
    let path = session_cache_path(app, profile_id)?;
    if !path.exists() {
        return Ok(None);
    }

    let content = fs::read_to_string(&path).map_err(|err| err.to_string())?;
    let session =
        serde_json::from_str::<DesktopSessionSecrets>(&content).map_err(|err| err.to_string())?;
    match entry.set_password(&content) {
        Ok(()) => {
            let _ = delete_session_file(app, profile_id);
        }
        Err(error) => {
            eprintln!(
                "desktop session keyring migration failed for profile={}, preserving file fallback: {}",
                profile_id, error
            );
        }
    }
    Ok(Some(session))
}

fn load_session_from_file(
    app: &AppHandle,
    profile_id: &str,
) -> Result<Option<DesktopSessionSecrets>, String> {
    let path = session_cache_path(app, profile_id)?;
    if !path.exists() {
        return Ok(None);
    }
    let content = fs::read_to_string(path).map_err(|err| err.to_string())?;
    serde_json::from_str(&content)
        .map(Some)
        .map_err(|err| err.to_string())
}

fn save_session_to_file(app: &AppHandle, session: &DesktopSessionSecrets) -> Result<(), String> {
    let path = session_cache_path(app, &session.profile_id)?;
    let payload = serde_json::to_string_pretty(session).map_err(|err| err.to_string())?;
    fs::write(&path, payload).map_err(|err| err.to_string())?;
    #[cfg(unix)]
    fs::set_permissions(&path, fs::Permissions::from_mode(0o600)).map_err(|err| err.to_string())?;
    Ok(())
}

fn finish_session_write(
    keyring_result: Result<(), String>,
    save_fallback: impl FnOnce() -> Result<(), String>,
    delete_legacy: impl FnOnce() -> Result<(), String>,
) -> Result<(), String> {
    match keyring_result {
        Ok(()) => {
            let _ = delete_legacy();
            Ok(())
        }
        Err(error) => {
            eprintln!(
                "desktop session keyring save failed, persisting file fallback instead: {error}"
            );
            save_fallback()
        }
    }
}

fn delete_session_file(app: &AppHandle, profile_id: &str) -> Result<(), String> {
    let path = session_cache_path(app, profile_id)?;
    if path.exists() {
        fs::remove_file(path).map_err(|err| err.to_string())?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::{decode_protected_session_or_fallback, finish_session_write};
    use std::cell::Cell;

    #[test]
    fn successful_keyring_write_cleans_legacy_file() {
        let saved_fallback = Cell::new(false);
        let cleaned = Cell::new(false);
        finish_session_write(
            Ok(()),
            || {
                saved_fallback.set(true);
                Ok(())
            },
            || {
                cleaned.set(true);
                Ok(())
            },
        )
        .unwrap();
        assert!(cleaned.get());
        assert!(!saved_fallback.get());
    }

    #[test]
    fn failed_keyring_write_persists_file_fallback() {
        let saved_fallback = Cell::new(false);
        let cleaned = Cell::new(false);
        finish_session_write(
            Err("keyring unavailable".to_string()),
            || {
                saved_fallback.set(true);
                Ok(())
            },
            || {
                cleaned.set(true);
                Ok(())
            },
        )
        .unwrap();
        assert!(saved_fallback.get());
        assert!(!cleaned.get());
    }

    #[test]
    fn invalid_protected_session_preserves_and_loads_legacy_file() {
        let loaded_fallback = Cell::new(false);
        let cleaned = Cell::new(false);
        let session = decode_protected_session_or_fallback(
            "{invalid",
            || {
                loaded_fallback.set(true);
                Ok(None)
            },
            || {
                cleaned.set(true);
                Ok(())
            },
        )
        .unwrap();

        assert!(session.is_none());
        assert!(loaded_fallback.get());
        assert!(!cleaned.get());
    }

    #[test]
    fn valid_protected_session_cleans_legacy_file() {
        let loaded_fallback = Cell::new(false);
        let cleaned = Cell::new(false);
        let payload = r#"{
            "profileId":"profile-1",
            "username":"user",
            "accessToken":"access",
            "refreshToken":"refresh",
            "expiresAt":"2026-07-16T00:00:00Z",
            "refreshExpiresAt":"2026-07-17T00:00:00Z"
        }"#;
        let session = decode_protected_session_or_fallback(
            payload,
            || {
                loaded_fallback.set(true);
                Ok(None)
            },
            || {
                cleaned.set(true);
                Ok(())
            },
        )
        .unwrap();

        assert!(session.is_some());
        assert!(!loaded_fallback.get());
        assert!(cleaned.get());
    }
}
