package io.github.magisk317.relay.backup

import android.content.Context
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.auth.AuthManager
import io.github.magisk317.relay.backup.drive.GoogleDriveBackupConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleDriveCloudBackupProvider(
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
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("Not logged in"))
        }

        googleDriveBackupManager.uploadBackup()
            .map { it.id }
            .logFailure("upload")
    }

    override suspend fun listBackups(): Result<List<CloudBackupMeta>> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.success(emptyList())
        }

        googleDriveBackupManager.listBackups()
            .map { backups ->
                backups.map {
                    CloudBackupMeta(it.id, it.name, it.size, it.modifiedTime, BackupSource.GOOGLE_DRIVE)
                }
            }
            .logFailure("list")
    }

    override suspend fun restoreFromBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("Not logged in"))
        }

        googleDriveBackupManager.restoreFromBackup(backupId).logFailure("restore")
    }

    override suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("Not logged in"))
        }

        googleDriveBackupManager.deleteBackup(backupId).logFailure("delete")
    }

    override suspend fun enableAutoBackup(enabled: Boolean) {
        CloudBackupSettingsStore.setAutoBackup(context, BackupSource.GOOGLE_DRIVE, enabled)
        XLog.i("Google Drive auto backup setting changed: enabled=%s", enabled)
    }

    override fun isAutoBackupEnabled(): Boolean {
        return CloudBackupSettingsStore.isAutoBackupEnabled(context, BackupSource.GOOGLE_DRIVE)
    }

    private fun <T> Result<T>.logFailure(operation: String): Result<T> {
        return onFailure { throwable ->
            XLog.e(
                "Google Drive %s backup failed: %s",
                operation,
                throwable.message ?: throwable.javaClass.simpleName,
            )
        }
    }
}
