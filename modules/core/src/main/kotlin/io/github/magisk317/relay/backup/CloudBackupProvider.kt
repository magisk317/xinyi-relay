package io.github.magisk317.relay.backup

import io.github.magisk317.relay.backup.drive.GoogleDriveBackupConfig

/**
 * Interface for cloud backup operations.
 * Both play and github flavors support Google Drive and WebDAV backup.
 */
interface CloudBackupProvider {
    fun isAvailable(): Boolean
    fun getBackupSource(): BackupSource
    suspend fun uploadBackup(): Result<String>
    suspend fun listBackups(): Result<List<CloudBackupMeta>>
    suspend fun restoreFromBackup(backupId: String): Result<Unit>
    suspend fun deleteBackup(backupId: String): Result<Unit>
    suspend fun enableAutoBackup(enabled: Boolean)
    fun isAutoBackupEnabled(): Boolean
}

interface GoogleDriveConfigurableBackupProvider {
    fun updateGoogleDriveConfig(config: GoogleDriveBackupConfig)
}

enum class BackupSource {
    GOOGLE_DRIVE,
    WEBDAV,
}

data class CloudBackupMeta(
    val id: String,
    val name: String,
    val size: Long,
    val modifiedTime: String,
    val source: BackupSource,
)

class NoOpCloudBackupProvider : CloudBackupProvider {
    override fun isAvailable(): Boolean = false
    override fun getBackupSource(): BackupSource = BackupSource.GOOGLE_DRIVE
    override suspend fun uploadBackup(): Result<String> = Result.failure(IllegalStateException("Not available"))
    override suspend fun listBackups(): Result<List<CloudBackupMeta>> = Result.success(emptyList())
    override suspend fun restoreFromBackup(backupId: String): Result<Unit> = Result.failure(IllegalStateException("Not available"))
    override suspend fun deleteBackup(backupId: String): Result<Unit> = Result.failure(IllegalStateException("Not available"))
    override suspend fun enableAutoBackup(enabled: Boolean) {}
    override fun isAutoBackupEnabled(): Boolean = false
}
