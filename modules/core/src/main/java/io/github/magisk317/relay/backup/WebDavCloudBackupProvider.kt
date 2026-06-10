package io.github.magisk317.relay.backup

import android.content.Context
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.backup.webdav.WebDavConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebDavCloudBackupProvider(
    private val context: Context,
    private val webDavBackupManager: WebDavBackupManager,
) : CloudBackupProvider {

    override fun isAvailable(): Boolean = true

    override fun getBackupSource(): BackupSource = BackupSource.WEBDAV

    override suspend fun uploadBackup(): Result<String> = withContext(Dispatchers.IO) {
        webDavBackupManager.uploadBackup()
            .map { it.id }
            .logFailure("upload")
    }

    override suspend fun listBackups(): Result<List<CloudBackupMeta>> = withContext(Dispatchers.IO) {
        webDavBackupManager.listBackups().logFailure("list")
    }

    override suspend fun restoreFromBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        webDavBackupManager.restoreFromBackup(backupId).logFailure("restore")
    }

    override suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        webDavBackupManager.deleteBackup(backupId).logFailure("delete")
    }

    override suspend fun enableAutoBackup(enabled: Boolean) {
        CloudBackupSettingsStore.setAutoBackup(context, BackupSource.WEBDAV, enabled)
        XLog.i("WebDAV auto backup setting changed: enabled=%s", enabled)
    }

    override fun isAutoBackupEnabled(): Boolean {
        return CloudBackupSettingsStore.isAutoBackupEnabled(context, BackupSource.WEBDAV)
    }

    suspend fun testConnection(): Result<Unit> {
        return webDavBackupManager.testConnection()
    }

    fun updateConfig(config: WebDavConfig) {
        webDavBackupManager.updateConfig(config)
    }

    private fun <T> Result<T>.logFailure(operation: String): Result<T> {
        return onFailure { throwable ->
            XLog.e(
                "WebDAV %s backup failed: %s",
                operation,
                throwable.message ?: throwable.javaClass.simpleName,
            )
        }
    }
}
