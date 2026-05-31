package io.github.magisk317.relay.ui.backup

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.auth.AuthManager
import io.github.magisk317.relay.auth.GoogleDriveAuthorizationRequiredException
import io.github.magisk317.relay.auth.GoogleSignInHelper
import io.github.magisk317.relay.backup.BackupSource
import io.github.magisk317.relay.backup.CloudBackupMeta
import io.github.magisk317.relay.backup.CloudBackupProvider
import io.github.magisk317.relay.backup.CloudBackupSettingsStore
import io.github.magisk317.relay.backup.GoogleDriveConfigurableBackupProvider
import io.github.magisk317.relay.backup.WebDavCloudBackupProvider
import io.github.magisk317.relay.backup.drive.GoogleDriveBackupConfig
import io.github.magisk317.relay.backup.drive.GoogleDriveBackupConfigStore
import io.github.magisk317.relay.backup.webdav.WebDavConfig
import io.github.magisk317.relay.backup.webdav.WebDavConfigStore
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.mobileui.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CloudBackupViewModel(application: Application) : AndroidViewModel(application), KoinComponent {

    private val cloudBackupProvider: CloudBackupProvider by inject()
    private val webDavCloudBackupProvider: WebDavCloudBackupProvider by inject()
    private val authManager: AuthManager by inject()
    private val googleSignInHelper: GoogleSignInHelper by inject()

    private val _backups = MutableStateFlow<List<CloudBackupMeta>>(emptyList())
    val backups: StateFlow<List<CloudBackupMeta>> = _backups.asStateFlow()

    private val _backupListMessage = MutableStateFlow<String?>(null)
    val backupListMessage: StateFlow<String?> = _backupListMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(false)
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _selectedSource = MutableStateFlow(defaultBackupSource(application))
    val selectedSource: StateFlow<BackupSource> = _selectedSource.asStateFlow()

    private val _webDavConfig = MutableStateFlow<WebDavConfig?>(null)
    val webDavConfig: StateFlow<WebDavConfig?> = _webDavConfig.asStateFlow()

    private val _googleDriveConfig = MutableStateFlow(GoogleDriveBackupConfig())
    val googleDriveConfig: StateFlow<GoogleDriveBackupConfig> = _googleDriveConfig.asStateFlow()

    private val _events = MutableSharedFlow<CloudBackupEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CloudBackupEvent> = _events.asSharedFlow()

    private var pendingAfterLoginAction: AfterLoginAction? = null
    private var pendingDriveAction: DriveAction? = null

    fun isAvailable(): Boolean = true

    fun hasGoogleDriveBackup(): Boolean = BuildConfig.HAS_CLOUD_BACKUP

    fun getBackupSource(): BackupSource = _selectedSource.value

    fun canUseCloudBackup(): Boolean {
        return when (_selectedSource.value) {
            BackupSource.GOOGLE_DRIVE -> hasGoogleDriveBackup() && cloudBackupProvider.isAvailable()
            BackupSource.WEBDAV -> _webDavConfig.value != null
        }
    }

    fun getGoogleSignInIntent(): Intent {
        return googleSignInHelper.getSignInIntent()
    }

    fun setPendingAfterLoginAction(action: AfterLoginAction) {
        pendingAfterLoginAction = action
    }

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _isLoading.value = true
            val account = googleSignInHelper.handleSignInResult(data)
            if (account != null) {
                val nextAction = pendingAfterLoginAction
                pendingAfterLoginAction = null
                val result = authManager.signInWithGoogle(account)
                if (result.isSuccess) {
                    _events.emit(CloudBackupEvent.LoginSuccess)
                    _isLoading.value = false
                    when (nextAction) {
                        AfterLoginAction.BackupNow -> backupNow()
                        null -> loadBackups()
                    }
                    return@launch
                }
                _events.emit(CloudBackupEvent.Error(string(R.string.cloud_backup_login_failed)))
            } else {
                pendingAfterLoginAction = null
                _events.emit(CloudBackupEvent.Error(string(R.string.cloud_backup_login_canceled)))
            }
            _isLoading.value = false
        }
    }

    init {
        val savedGoogleConfig = GoogleDriveBackupConfigStore.getConfig(getApplication()).normalized()
        _googleDriveConfig.value = savedGoogleConfig
        updateGoogleDriveProviderConfig(savedGoogleConfig)

        // Load persisted WebDAV config
        val savedConfig = WebDavConfigStore.getConfig(getApplication())
        if (savedConfig != null) {
            val normalizedConfig = savedConfig.normalizedOrNull()
            if (normalizedConfig != null) {
                _webDavConfig.value = normalizedConfig
                webDavCloudBackupProvider.updateConfig(normalizedConfig)
            } else {
                WebDavConfigStore.removeConfig(getApplication())
            }
        }
        _autoBackupEnabled.value = getActiveProvider().isAutoBackupEnabled()
    }

    fun switchBackupSource(source: BackupSource) {
        _selectedSource.value = if (source == BackupSource.GOOGLE_DRIVE && !hasGoogleDriveBackup()) {
            BackupSource.WEBDAV
        } else {
            source
        }
        _autoBackupEnabled.value = getActiveProvider().isAutoBackupEnabled()
    }

    fun updateWebDavConfig(config: WebDavConfig): WebDavConfig? {
        val normalizedConfig = config.normalizedOrNull()
        if (normalizedConfig == null) {
            _events.tryEmit(CloudBackupEvent.Error(string(R.string.cloud_backup_webdav_required)))
            return null
        }
        _webDavConfig.value = normalizedConfig
        webDavCloudBackupProvider.updateConfig(normalizedConfig)
        WebDavConfigStore.saveConfig(getApplication(), normalizedConfig)
        return normalizedConfig
    }

    fun updateGoogleDriveConfig(config: GoogleDriveBackupConfig): GoogleDriveBackupConfig {
        val normalizedConfig = config.normalized()
        _googleDriveConfig.value = normalizedConfig
        updateGoogleDriveProviderConfig(normalizedConfig)
        GoogleDriveBackupConfigStore.saveConfig(getApplication(), normalizedConfig)
        return normalizedConfig
    }

    fun removeWebDavConfig() {
        _webDavConfig.value = null
        WebDavConfigStore.removeConfig(getApplication())
    }

    fun testWebDavConnection() {
        val config = _webDavConfig.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = webDavCloudBackupProvider.testConnection()
            result.fold(
                onSuccess = { _events.emit(CloudBackupEvent.WebDavConnectionSuccess) },
                onFailure = { _events.emit(CloudBackupEvent.Error(userMessage(it, string(R.string.cloud_backup_webdav_connection_failed)))) },
            )
            _isLoading.value = false
        }
    }

    private fun getActiveProvider(): CloudBackupProvider {
        return when (_selectedSource.value) {
            BackupSource.GOOGLE_DRIVE -> cloudBackupProvider
            BackupSource.WEBDAV -> webDavCloudBackupProvider
        }
    }

    private fun updateGoogleDriveProviderConfig(config: GoogleDriveBackupConfig) {
        (cloudBackupProvider as? GoogleDriveConfigurableBackupProvider)?.updateGoogleDriveConfig(config)
    }

    fun loadBackups() {
        if (!canUseCloudBackup()) {
            _backups.value = emptyList()
            _backupListMessage.value = unavailableMessage()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _backupListMessage.value = null
            val provider = getActiveProvider()
            val result = provider.listBackups()
            if (result.isSuccess) {
                _backups.value = result.getOrThrow()
                _backupListMessage.value = if (_backups.value.isEmpty()) {
                    string(R.string.cloud_backup_no_backups)
                } else {
                    null
                }
            } else {
                val errorMessage = userMessage(result.exceptionOrNull(), string(R.string.cloud_backup_load_failed))
                _backupListMessage.value = errorMessage
                handleDriveFailure(result.exceptionOrNull(), DriveAction.LoadBackups, errorMessage)
            }
            _isLoading.value = false
        }
    }

    fun backupNow() {
        if (!canUseCloudBackup()) {
            val message = unavailableMessage()
            _backupListMessage.value = message
            _events.tryEmit(CloudBackupEvent.Error(message))
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val provider = getActiveProvider()
            val result = provider.uploadBackup()
            if (result.isSuccess) {
                _events.emit(CloudBackupEvent.BackupSuccess)
                loadBackups()
            } else {
                handleDriveFailure(result.exceptionOrNull(), DriveAction.BackupNow, string(R.string.backup_failed))
            }
            _isLoading.value = false
        }
    }

    fun restoreBackup(backupId: String) {
        if (!canUseCloudBackup()) return
        viewModelScope.launch {
            _isLoading.value = true
            val provider = getActiveProvider()
            val result = provider.restoreFromBackup(backupId)
            if (result.isSuccess) {
                _events.emit(CloudBackupEvent.RestoreSuccess)
            } else {
                handleDriveFailure(result.exceptionOrNull(), DriveAction.Restore(backupId), string(R.string.restore_failed))
            }
            _isLoading.value = false
        }
    }

    fun deleteBackup(backupId: String) {
        viewModelScope.launch {
            val provider = getActiveProvider()
            val result = provider.deleteBackup(backupId)
            if (result.isSuccess) {
                loadBackups()
            } else {
                handleDriveFailure(result.exceptionOrNull(), DriveAction.Delete(backupId), string(R.string.cloud_backup_delete_failed))
            }
        }
    }

    fun handleGoogleDriveAuthorizationResult(granted: Boolean) {
        val action = pendingDriveAction
        pendingDriveAction = null
        if (!granted) {
            viewModelScope.launch {
                _events.emit(CloudBackupEvent.Error(string(R.string.cloud_backup_drive_authorization_canceled)))
            }
            return
        }
        when (action) {
            DriveAction.BackupNow -> backupNow()
            DriveAction.LoadBackups -> loadBackups()
            is DriveAction.Restore -> restoreBackup(action.backupId)
            is DriveAction.Delete -> deleteBackup(action.backupId)
            null -> loadBackups()
        }
    }

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            getActiveProvider().enableAutoBackup(enabled)
            _autoBackupEnabled.value = getActiveProvider().isAutoBackupEnabled()
        }
    }

    private suspend fun handleDriveFailure(error: Throwable?, retryAction: DriveAction, fallbackMessage: String) {
        if (_selectedSource.value == BackupSource.GOOGLE_DRIVE && error is GoogleDriveAuthorizationRequiredException) {
            pendingDriveAction = retryAction
            _events.emit(CloudBackupEvent.GoogleDriveAuthorizationRequired(error.authorizationIntent))
        } else {
            _events.emit(CloudBackupEvent.Error(userMessage(error, fallbackMessage)))
        }
    }

    private fun WebDavConfig.normalizedOrNull(): WebDavConfig? {
        val trimmedServerUrl = serverUrl.trim()
        val trimmedUsername = username.trim()
        if (trimmedServerUrl.isBlank() || trimmedUsername.isBlank() || password.isBlank()) {
            return null
        }
        return copy(
            serverUrl = trimmedServerUrl,
            username = trimmedUsername,
            remotePath = remotePath.trim().ifBlank { WebDavConfig.DEFAULT_REMOTE_PATH },
        )
    }

    private fun GoogleDriveBackupConfig.normalized(): GoogleDriveBackupConfig {
        val normalizedPath = folderPath.trim().ifBlank { GoogleDriveBackupConfig.DEFAULT_FOLDER_PATH }
        return copy(folderPath = normalizedPath)
    }

    private fun string(resId: Int): String = getApplication<Application>().getString(resId)

    private fun unavailableMessage(): String = when (_selectedSource.value) {
        BackupSource.GOOGLE_DRIVE -> string(R.string.cloud_backup_list_sign_in_required)
        BackupSource.WEBDAV -> string(R.string.cloud_backup_list_webdav_config_required)
    }

    private fun userMessage(error: Throwable?, fallbackMessage: String): String {
        val rawMessage = error?.message.orEmpty()
        return when {
            rawMessage.contains("Google Drive API has not been used", ignoreCase = true) ||
                rawMessage.contains("Google Drive API disabled", ignoreCase = true) ||
                rawMessage.contains("it is disabled", ignoreCase = true) ->
                string(R.string.cloud_backup_drive_api_disabled)
            rawMessage.contains("Not logged in", ignoreCase = true) ||
                rawMessage.contains("missing access token", ignoreCase = true) ->
                string(R.string.cloud_backup_list_sign_in_required)
            rawMessage.contains("WebDAV not configured", ignoreCase = true) ->
                string(R.string.cloud_backup_list_webdav_config_required)
            rawMessage.contains("Local backup", ignoreCase = true) ->
                string(R.string.cloud_backup_local_export_failed)
            rawMessage.contains("Upload failed", ignoreCase = true) ->
                string(R.string.cloud_backup_upload_failed)
            rawMessage.contains("List failed", ignoreCase = true) ->
                string(R.string.cloud_backup_load_failed)
            rawMessage.contains("Download failed", ignoreCase = true) ->
                string(R.string.cloud_backup_download_failed)
            rawMessage.contains("Delete failed", ignoreCase = true) ->
                string(R.string.cloud_backup_delete_failed)
            rawMessage.contains("Failed to create directory", ignoreCase = true) ->
                string(R.string.cloud_backup_webdav_directory_failed)
            rawMessage.contains("Restore failed", ignoreCase = true) ->
                string(R.string.restore_failed)
            else -> fallbackMessage
        }
    }

    sealed class CloudBackupEvent {
        data object BackupSuccess : CloudBackupEvent()
        data object RestoreSuccess : CloudBackupEvent()
        data object WebDavConnectionSuccess : CloudBackupEvent()
        data object LoginSuccess : CloudBackupEvent()
        data class GoogleDriveAuthorizationRequired(val intent: Intent) : CloudBackupEvent()
        data class Error(val message: String) : CloudBackupEvent()
    }

    sealed class AfterLoginAction {
        data object BackupNow : AfterLoginAction()
    }

    private sealed class DriveAction {
        data object BackupNow : DriveAction()
        data object LoadBackups : DriveAction()
        data class Restore(val backupId: String) : DriveAction()
        data class Delete(val backupId: String) : DriveAction()
    }

    private companion object {
        fun defaultBackupSource(application: Application): BackupSource {
            val savedSource = CloudBackupSettingsStore.getSettings(application).autoBackupSource
            return if (savedSource == BackupSource.GOOGLE_DRIVE && !BuildConfig.HAS_CLOUD_BACKUP) {
                BackupSource.WEBDAV
            } else {
                savedSource
            }
        }
    }
}
