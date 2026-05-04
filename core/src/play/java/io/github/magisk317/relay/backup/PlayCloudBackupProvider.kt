package io.github.magisk317.relay.backup

import io.github.magisk317.relay.auth.FirebaseAuthManager
import io.github.magisk317.relay.billing.SubscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class PlayCloudBackupProvider(
    private val googleDriveBackupManager: GoogleDriveBackupManager,
    private val authManager: FirebaseAuthManager,
    private val subscriptionManager: SubscriptionManager,
) : CloudBackupProvider {

    override fun isAvailable(): Boolean = authManager.isLoggedIn() && subscriptionManager.isActive()

    override fun isSubscriptionRequired(): Boolean = true

    override suspend fun uploadBackup(): Result<String> = withContext(Dispatchers.IO) {
        if (!authManager.isLoggedIn()) {
            return@withContext Result.failure(IllegalStateException("Not logged in"))
        }
        if (!subscriptionManager.isActive()) {
            return@withContext Result.failure(IllegalStateException("Subscription not active"))
        }

        try {
            val result = googleDriveBackupManager.uploadBackup()
            result.map { it.id }
        } catch (e: Exception) {
            Timber.e(e, "Upload backup failed")
            Result.failure(e)
        }
    }

    override suspend fun listBackups(): Result<List<CloudBackupMeta>> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.success(emptyList())
        }

        try {
            val result = googleDriveBackupManager.listBackups().getOrThrow()
            Result.success(result.map { CloudBackupMeta(it.id, it.name, it.size, it.modifiedTime) })
        } catch (e: Exception) {
            Timber.e(e, "List backups failed")
            Result.failure(e)
        }
    }

    override suspend fun restoreFromBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("Not available"))
        }

        try {
            googleDriveBackupManager.restoreFromBackup(backupId)
        } catch (e: Exception) {
            Timber.e(e, "Restore backup failed")
            Result.failure(e)
        }
    }

    override suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("Not available"))
        }

        try {
            googleDriveBackupManager.deleteBackup(backupId)
        } catch (e: Exception) {
            Timber.e(e, "Delete backup failed")
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
}
