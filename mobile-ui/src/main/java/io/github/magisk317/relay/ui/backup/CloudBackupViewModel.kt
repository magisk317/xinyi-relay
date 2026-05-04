package io.github.magisk317.relay.ui.backup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.auth.FirebaseAuthManager
import io.github.magisk317.relay.backup.CloudBackupMeta
import io.github.magisk317.relay.backup.CloudBackupProvider
import io.github.magisk317.relay.billing.BillingProvider
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
    private val authManager: FirebaseAuthManager by inject()
    private val billingProvider: BillingProvider by inject()

    private val _backups = MutableStateFlow<List<CloudBackupMeta>>(emptyList())
    val backups: StateFlow<List<CloudBackupMeta>> = _backups.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(false)
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _events = MutableSharedFlow<CloudBackupEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CloudBackupEvent> = _events.asSharedFlow()

    fun isAvailable(): Boolean = BuildConfig.HAS_CLOUD_BACKUP && cloudBackupProvider.isAvailable()

    fun isSubscriptionActive(): Boolean = billingProvider.isSubscriptionActive()

    fun isLoggedIn(): Boolean = authManager.isLoggedIn()

    fun canUseCloudBackup(): Boolean = isLoggedIn() && isSubscriptionActive()

    init {
        _autoBackupEnabled.value = cloudBackupProvider.isAutoBackupEnabled()
    }

    fun loadBackups() {
        if (!canUseCloudBackup()) return
        viewModelScope.launch {
            _isLoading.value = true
            val result = cloudBackupProvider.listBackups()
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
            val result = cloudBackupProvider.uploadBackup()
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
            val result = cloudBackupProvider.restoreFromBackup(backupId)
            result.fold(
                onSuccess = { _events.emit(CloudBackupEvent.RestoreSuccess) },
                onFailure = { _events.emit(CloudBackupEvent.Error(it.message ?: "Restore failed")) },
            )
            _isLoading.value = false
        }
    }

    fun deleteBackup(backupId: String) {
        viewModelScope.launch {
            val result = cloudBackupProvider.deleteBackup(backupId)
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
        data class Error(val message: String) : CloudBackupEvent()
    }
}
