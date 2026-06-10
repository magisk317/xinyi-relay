use super::*;

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
    serde_json::from_str(&content).map_err(|err| err.to_string())
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
    let entry = session_entry(profile_id)?;
    match entry.get_password() {
        Ok(value) => serde_json::from_str::<DesktopSessionSecrets>(&value)
            .map(Some)
            .map_err(|err| err.to_string()),
        Err(KeyringError::NoEntry) => load_session_from_file(app, profile_id),
        Err(err) => {
            eprintln!(
                "desktop session keyring load failed for profile={}, falling back to file: {}",
                profile_id, err
            );
            load_session_from_file(app, profile_id)
        }
    }
}

pub(crate) fn save_session_for_profile(
    app: &AppHandle,
    session: &DesktopSessionSecrets,
) -> Result<(), String> {
    let file_result = save_session_to_file(app, session);
    let entry = session_entry(&session.profile_id)?;
    let payload = serde_json::to_string(session).map_err(|err| err.to_string())?;
    let keyring_result = entry.set_password(&payload).map_err(|err| err.to_string());

    match (file_result, keyring_result) {
        (Ok(_), Ok(_)) => Ok(()),
        (Ok(_), Err(err)) => {
            eprintln!(
                "desktop session keyring save failed for profile={}, persisted file fallback instead: {}",
                session.profile_id, err
            );
            Ok(())
        }
        (Err(err), Ok(_)) => {
            eprintln!(
                "desktop session file save failed for profile={}, keyring save succeeded: {}",
                session.profile_id, err
            );
            Ok(())
        }
        (Err(file_err), Err(keyring_err)) => Err(format!(
            "failed to persist desktop session (file: {}; keyring: {})",
            file_err, keyring_err
        )),
    }
}

pub(crate) fn delete_session_for_profile(
    app: &AppHandle,
    profile_id: &str,
) -> Result<(), String> {
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

fn load_session_from_file(
    app: &AppHandle,
    profile_id: &str,
) -> Result<Option<DesktopSessionSecrets>, String> {
    let path = session_cache_path(app, profile_id)?;
    if !path.exists() {
        return Ok(None);
    }

    let content = fs::read_to_string(&path).map_err(|err| err.to_string())?;
    serde_json::from_str::<DesktopSessionSecrets>(&content)
        .map(Some)
        .map_err(|err| err.to_string())
}

fn save_session_to_file(app: &AppHandle, session: &DesktopSessionSecrets) -> Result<(), String> {
    let path = session_cache_path(app, &session.profile_id)?;
    let payload = serde_json::to_string_pretty(session).map_err(|err| err.to_string())?;
    fs::write(&path, payload).map_err(|err| err.to_string())?;
    #[cfg(unix)]
    {
        fs::set_permissions(&path, fs::Permissions::from_mode(0o600))
            .map_err(|err| err.to_string())?;
    }
    Ok(())
}

fn delete_session_file(app: &AppHandle, profile_id: &str) -> Result<(), String> {
    let path = session_cache_path(app, profile_id)?;
    if path.exists() {
        fs::remove_file(path).map_err(|err| err.to_string())?;
    }
    Ok(())
}
