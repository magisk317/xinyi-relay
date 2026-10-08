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
import io.github.magisk317.xposed.logging.MagiskOtel

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
                emitBackup(result = "skip", reason = "disabled", source = reason)
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
                        emitBackup(result = "skip", reason = "webdav_config_missing", source = reason)
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
                emitBackup(
                    result = "skip",
                    reason = "provider_unavailable",
                    source = reason,
                    backupSource = settings.autoBackupSource.name,
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
                    emitBackup(
                        result = "ok",
                        reason = "uploaded",
                        source = reason,
                        backupSource = settings.autoBackupSource.name,
                    )
                },
                onFailure = { error ->
                    XLog.e(
                        "Auto cloud backup failed: source=%s reason=%s error=%s",
                        settings.autoBackupSource.name,
                        reason,
                        error.message ?: error.javaClass.simpleName,
                    )
                    emitBackup(
                        result = "error",
                        reason = "upload_failed",
                        source = reason,
                        backupSource = settings.autoBackupSource.name,
                        statusOk = false,
                        errorClass = error.javaClass.simpleName,
                    )
                },
            )
        }
    }

    private fun emitBackup(
        result: String,
        reason: String,
        source: String,
        backupSource: String? = null,
        statusOk: Boolean = true,
        errorClass: String? = null,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "app",
            "stage" to "auto_cloud",
            "reason" to reason,
            "source" to source,
        )
        if (!backupSource.isNullOrBlank()) {
            attrs["sender_type"] = backupSource
        }
        if (!errorClass.isNullOrBlank()) {
            attrs["error_class"] = errorClass
        }
        MagiskOtel.event(name = "prefs.backup", attributes = attrs, statusOk = statusOk)
    }

    private companion object {
        const val AUTO_BACKUP_DEBOUNCE_MS = 1500L
    }
}
