package io.github.magisk317.relay.ui.backup

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.auth.AuthManager
import io.github.magisk317.relay.auth.GoogleSignInHelper
import io.github.magisk317.relay.backup.BackupSource
import io.github.magisk317.relay.backup.CloudBackupMeta
import io.github.magisk317.relay.backup.CloudBackupProvider
import io.github.magisk317.relay.backup.WebDavCloudBackupProvider
import io.github.magisk317.relay.backup.webdav.WebDavConfig
import io.github.magisk317.relay.backup.webdav.WebDavConfigStore
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(false)
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _selectedSource = MutableStateFlow(BackupSource.GOOGLE_DRIVE)
    val selectedSource: StateFlow<BackupSource> = _selectedSource.asStateFlow()

    private val _webDavConfig = MutableStateFlow<WebDavConfig?>(null)
    val webDavConfig: StateFlow<WebDavConfig?> = _webDavConfig.asStateFlow()

    private val _events = MutableSharedFlow<CloudBackupEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CloudBackupEvent> = _events.asSharedFlow()

    fun isAvailable(): Boolean = BuildConfig.HAS_CLOUD_BACKUP

    fun getBackupSource(): BackupSource = _selectedSource.value

    fun canUseCloudBackup(): Boolean {
        return when (_selectedSource.value) {
            BackupSource.GOOGLE_DRIVE -> cloudBackupProvider.isAvailable()
            BackupSource.WEBDAV -> _webDavConfig.value != null
        }
    }

    fun getGoogleSignInIntent(): Intent {
        return googleSignInHelper.getSignInIntent()
    }

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _isLoading.value = true
            val account = googleSignInHelper.handleSignInResult(data)
            if (account != null) {
                val result = authManager.signInWithGoogle(account)
                result.fold(
                    onSuccess = {
                        _events.emit(CloudBackupEvent.LoginSuccess)
                        loadBackups()
                    },
                    onFailure = { _events.emit(CloudBackupEvent.Error(it.message ?: "Login failed")) },
                )
            }
            _isLoading.value = false
        }
    }

    init {
        _autoBackupEnabled.value = cloudBackupProvider.isAutoBackupEnabled()
        // Load persisted WebDAV config
        val savedConfig = WebDavConfigStore.getConfig(getApplication())
        if (savedConfig != null) {
            _webDavConfig.value = savedConfig
            webDavCloudBackupProvider.updateConfig(savedConfig)
        }
    }

    fun switchBackupSource(source: BackupSource) {
        _selectedSource.value = source
        loadBackups()
    }

    fun updateWebDavConfig(config: WebDavConfig) {
        _webDavConfig.value = config
        webDavCloudBackupProvider.updateConfig(config)
        // Persist to DataStore
        WebDavConfigStore.saveConfig(getApplication(), config)
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
                onFailure = { _events.emit(CloudBackupEvent.Error(it.message ?: "Connection failed")) },
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

    fun loadBackups() {
        if (!canUseCloudBackup()) return
        viewModelScope.launch {
            _isLoading.value = true
            val provider = getActiveProvider()
            val result = provider.listBackups()
            result.fold(
                onSuccess = { _backups.value = it },
                onFailure = { _events.emit(CloudBackupEvent.Error(it.message ?: "Failed to load")) },
            )
            _isLoading.value = false
        }
    }

    fun backupNow() {
        if (!canUseCloudBackup()) return
        viewModelScope.launch {
            _isLoading.value = true
            val provider = getActiveProvider()
            val result = provider.uploadBackup()
            result.fold(
                onSuccess = {
                    _events.emit(CloudBackupEvent.BackupSuccess)
                    loadBackups()
                },
                onFailure = { _events.emit(CloudBackupEvent.Error(it.message ?: "Backup failed")) },
            )
            _isLoading.value = false
        }
    }

    fun restoreBackup(backupId: String) {
        if (!canUseCloudBackup()) return
        viewModelScope.launch {
            _isLoading.value = true
            val provider = getActiveProvider()
            val result = provider.restoreFromBackup(backupId)
            result.fold(
                onSuccess = { _events.emit(CloudBackupEvent.RestoreSuccess) },
                onFailure = { _events.emit(CloudBackupEvent.Error(it.message ?: "Restore failed")) },
            )
            _isLoading.value = false
        }
    }

    fun deleteBackup(backupId: String) {
        viewModelScope.launch {
            val provider = getActiveProvider()
            val result = provider.deleteBackup(backupId)
            result.fold(
                onSuccess = { loadBackups() },
                onFailure = { _events.emit(CloudBackupEvent.Error(it.message ?: "Delete failed")) },
            )
        }
    }

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            cloudBackupProvider.enableAutoBackup(enabled)
            _autoBackupEnabled.value = enabled
        }
    }

    sealed class CloudBackupEvent {
        data object BackupSuccess : CloudBackupEvent()
        data object RestoreSuccess : CloudBackupEvent()
        data object WebDavConnectionSuccess : CloudBackupEvent()
        data object LoginSuccess : CloudBackupEvent()
        data class Error(val message: String) : CloudBackupEvent()
    }
}
