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
        try {
            val result = webDavBackupManager.uploadBackup()
            result.map { it.id }
        } catch (e: Exception) {
            XLog.e("WebDAV upload backup failed: %s", e.message ?: e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    override suspend fun listBackups(): Result<List<CloudBackupMeta>> = withContext(Dispatchers.IO) {
        try {
            webDavBackupManager.listBackups()
        } catch (e: Exception) {
            XLog.e("WebDAV list backups failed: %s", e.message ?: e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    override suspend fun restoreFromBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            webDavBackupManager.restoreFromBackup(backupId)
        } catch (e: Exception) {
            XLog.e("WebDAV restore backup failed: %s", e.message ?: e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    override suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            webDavBackupManager.deleteBackup(backupId)
        } catch (e: Exception) {
            XLog.e("WebDAV delete backup failed: %s", e.message ?: e.javaClass.simpleName)
            Result.failure(e)
        }
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
}
