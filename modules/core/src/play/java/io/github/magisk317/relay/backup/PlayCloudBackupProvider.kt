package io.github.magisk317.relay.backup

import android.content.Context
import io.github.magisk317.relay.auth.FirebaseAuthManager
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.backup.drive.GoogleDriveBackupConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlayCloudBackupProvider(
    private val context: Context,
    private val googleDriveBackupManager: GoogleDriveBackupManager,
    private val authManager: FirebaseAuthManager,
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

        try {
            val result = googleDriveBackupManager.uploadBackup()
            result.map { it.id }
        } catch (e: Exception) {
            XLog.e("Google Drive upload backup failed: %s", e.message ?: e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    override suspend fun listBackups(): Result<List<CloudBackupMeta>> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val result = googleDriveBackupManager.listBackups().getOrThrow()
            Result.success(result.map { CloudBackupMeta(it.id, it.name, it.size, it.modifiedTime, BackupSource.GOOGLE_DRIVE) })
        } catch (e: Exception) {
            XLog.e("Google Drive list backups failed: %s", e.message ?: e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    override suspend fun restoreFromBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("Not logged in"))
        }

        try {
            googleDriveBackupManager.restoreFromBackup(backupId)
        } catch (e: Exception) {
            XLog.e("Google Drive restore backup failed: %s", e.message ?: e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    override suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("Not logged in"))
        }

        try {
            googleDriveBackupManager.deleteBackup(backupId)
        } catch (e: Exception) {
            XLog.e("Google Drive delete backup failed: %s", e.message ?: e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    override suspend fun enableAutoBackup(enabled: Boolean) {
        CloudBackupSettingsStore.setAutoBackup(context, BackupSource.GOOGLE_DRIVE, enabled)
        XLog.i("Google Drive auto backup setting changed: enabled=%s", enabled)
    }

    override fun isAutoBackupEnabled(): Boolean {
        return CloudBackupSettingsStore.isAutoBackupEnabled(context, BackupSource.GOOGLE_DRIVE)
    }
}
