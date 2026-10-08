package io.github.magisk317.relay.backup.drive

import kotlinx.serialization.Serializable

@Serializable
data class GoogleDriveBackupConfig(
    val folderPath: String = DEFAULT_FOLDER_PATH,
) {
    companion object {
        const val DEFAULT_FOLDER_PATH = "/xinyi-relay/backups/"
    }
}
