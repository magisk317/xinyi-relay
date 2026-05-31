package io.github.magisk317.relay.backup

import android.content.Context
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.backup.drive.GoogleDriveBackupConfigStore
import io.github.magisk317.relay.backup.webdav.WebDavConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AutoCloudBackupCoordinator(
    private val context: Context,
    private val googleDriveProvider: CloudBackupProvider,
    private val webDavProvider: WebDavCloudBackupProvider,
) {
    private val mutex = Mutex()
    private var pendingJob: Job? = null

    fun schedule(scope: CoroutineScope, reason: String) {
        pendingJob?.cancel()
        pendingJob = scope.launch(Dispatchers.IO) {
            delay(AUTO_BACKUP_DEBOUNCE_MS)
            runBackup(reason)
        }
    }

    private suspend fun runBackup(reason: String) {
        mutex.withLock {
            val settings = CloudBackupSettingsStore.getSettings(context)
            if (!settings.autoBackupEnabled) {
                XLog.i("Auto cloud backup skipped: disabled reason=%s", reason)
                return
            }

            val provider = when (settings.autoBackupSource) {
                BackupSource.GOOGLE_DRIVE -> {
                    val config = GoogleDriveBackupConfigStore.getConfig(context)
                    (googleDriveProvider as? GoogleDriveConfigurableBackupProvider)?.updateGoogleDriveConfig(config)
                    googleDriveProvider
                }
                BackupSource.WEBDAV -> {
                    val config = WebDavConfigStore.getConfig(context)
                    if (config == null) {
                        XLog.w("Auto cloud backup skipped: WebDAV config missing reason=%s", reason)
                        return
                    }
                    webDavProvider.updateConfig(config)
                    webDavProvider
                }
            }

            if (!provider.isAvailable()) {
                XLog.w(
                    "Auto cloud backup skipped: provider unavailable source=%s reason=%s",
                    settings.autoBackupSource.name,
                    reason,
                )
                return
            }

            XLog.i(
                "Auto cloud backup start: source=%s reason=%s",
                settings.autoBackupSource.name,
                reason,
            )
            val result = provider.uploadBackup()
            result.fold(
                onSuccess = { backupId ->
                    XLog.i(
                        "Auto cloud backup success: source=%s reason=%s backupId=%s",
                        settings.autoBackupSource.name,
                        reason,
                        backupId,
                    )
                },
                onFailure = { error ->
                    XLog.e(
                        "Auto cloud backup failed: source=%s reason=%s error=%s",
                        settings.autoBackupSource.name,
                        reason,
                        error.message ?: error.javaClass.simpleName,
                    )
                },
            )
        }
    }

    private companion object {
        const val AUTO_BACKUP_DEBOUNCE_MS = 1500L
    }
}
