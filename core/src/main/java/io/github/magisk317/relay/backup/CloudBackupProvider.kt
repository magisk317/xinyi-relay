package io.github.magisk317.relay.backup

/**
 * Interface for cloud backup operations.
 * Real implementation lives in play flavor; github gets a no-op stub.
 */
interface CloudBackupProvider {
    fun isAvailable(): Boolean
    fun isSubscriptionRequired(): Boolean
    suspend fun uploadBackup(): Result<String>
    suspend fun listBackups(): Result<List<CloudBackupMeta>>
    suspend fun restoreFromBackup(backupId: String): Result<Unit>
    suspend fun deleteBackup(backupId: String): Result<Unit>
    suspend fun enableAutoBackup(enabled: Boolean)
    fun isAutoBackupEnabled(): Boolean
}

data class CloudBackupMeta(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedTime: String,
)

class NoOpCloudBackupProvider : CloudBackupProvider {
    override fun isAvailable(): Boolean = false
    override fun isSubscriptionRequired(): Boolean = true
    override suspend fun uploadBackup(): Result<String> = Result.failure(IllegalStateException("Not available"))
    override suspend fun listBackups(): Result<List<CloudBackupMeta>> = Result.success(emptyList())
    override suspend fun restoreFromBackup(backupId: String): Result<Unit> = Result.failure(IllegalStateException("Not available"))
    override suspend fun deleteBackup(backupId: String): Result<Unit> = Result.failure(IllegalStateException("Not available"))
    override suspend fun enableAutoBackup(enabled: Boolean) {}
    override fun isAutoBackupEnabled(): Boolean = false
}
