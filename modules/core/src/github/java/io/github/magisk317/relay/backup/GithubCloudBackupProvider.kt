package io.github.magisk317.relay.backup

import android.content.Context
import io.github.magisk317.relay.auth.AuthManager
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.backup.drive.GoogleDriveBackupConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GithubCloudBackupProvider(
    private val context: Context,
    private val googleDriveBackupManager: GoogleDriveBackupManager,
    private val authManager: AuthManager,
) : CloudBackupProvider, GoogleDriveConfigurableBackupProvider {

    override fun isAvailable(): Boolean = authManager.isLoggedIn()

    override fun getBackupSource(): BackupSource = BackupSource.GOOGLE_DRIVE

    override fun updateGoogleDriveConfig(config: GoogleDriveBackupConfig) {
        googleDriveBackupManager.updateConfig(config)
    }

    override suspend fun uploadBackup(): Result<String> = withContext(Dispatchers.IO) {
        if (!authManager.isLoggedIn()) {
            return@withContext Result.failure(IllegalStateException("Not logged in"))
        }

        googleDriveBackupManager.uploadBackup()
            .map { it.id }
            .onFailure { error -> XLog.e("Google Drive upload backup failed: %s", error.message ?: error.javaClass.simpleName) }
    }

    override suspend fun listBackups(): Result<List<CloudBackupMeta>> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.success(emptyList())
        }

        googleDriveBackupManager.listBackups()
            .map { result ->
                result.map { CloudBackupMeta(it.id, it.name, it.size, it.modifiedTime, BackupSource.GOOGLE_DRIVE) }
            }
            .onFailure { error -> XLog.e("Google Drive list backups failed: %s", error.message ?: error.javaClass.simpleName) }
    }

    override suspend fun restoreFromBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("Not logged in"))
        }

        googleDriveBackupManager.restoreFromBackup(backupId)
            .onFailure { error -> XLog.e("Google Drive restore backup failed: %s", error.message ?: error.javaClass.simpleName) }
    }

    override suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("Not logged in"))
        }

        googleDriveBackupManager.deleteBackup(backupId)
            .onFailure { error -> XLog.e("Google Drive delete backup failed: %s", error.message ?: error.javaClass.simpleName) }
    }

    override suspend fun enableAutoBackup(enabled: Boolean) {
        CloudBackupSettingsStore.setAutoBackup(context, BackupSource.GOOGLE_DRIVE, enabled)
        XLog.i("Google Drive auto backup setting changed: enabled=%s", enabled)
    }

    override fun isAutoBackupEnabled(): Boolean {
        return CloudBackupSettingsStore.isAutoBackupEnabled(context, BackupSource.GOOGLE_DRIVE)
    }
}
