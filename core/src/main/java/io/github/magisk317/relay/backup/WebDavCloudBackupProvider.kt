package io.github.magisk317.relay.backup

import io.github.magisk317.relay.backup.webdav.WebDavConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class WebDavCloudBackupProvider(
    private val webDavBackupManager: WebDavBackupManager,
) : CloudBackupProvider {

    override fun isAvailable(): Boolean = true

    override fun getBackupSource(): BackupSource = BackupSource.WEBDAV

    override suspend fun uploadBackup(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = webDavBackupManager.uploadBackup()
            result.map { it.id }
        } catch (e: Exception) {
            Timber.e(e, "WebDAV upload backup failed")
            Result.failure(e)
        }
    }

    override suspend fun listBackups(): Result<List<CloudBackupMeta>> = withContext(Dispatchers.IO) {
        try {
            webDavBackupManager.listBackups()
        } catch (e: Exception) {
            Timber.e(e, "WebDAV list backups failed")
            Result.failure(e)
        }
    }

    override suspend fun restoreFromBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            webDavBackupManager.restoreFromBackup(backupId)
        } catch (e: Exception) {
            Timber.e(e, "WebDAV restore backup failed")
            Result.failure(e)
        }
    }

    override suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            webDavBackupManager.deleteBackup(backupId)
        } catch (e: Exception) {
            Timber.e(e, "WebDAV delete backup failed")
            Result.failure(e)
        }
    }

    override suspend fun enableAutoBackup(enabled: Boolean) {
        // TODO: Implement auto backup scheduling with WorkManager
    }

    override fun isAutoBackupEnabled(): Boolean {
        // TODO: Check auto backup setting
        return false
    }

    suspend fun testConnection(): Result<Unit> {
        return webDavBackupManager.testConnection()
    }

    fun updateConfig(config: WebDavConfig) {
        webDavBackupManager.updateConfig(config)
    }
}
