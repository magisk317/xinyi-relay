package io.github.magisk317.relay.backup

import kotlinx.serialization.Serializable

@Serializable
data class CloudBackupSettings(
    val autoBackupEnabled: Boolean = false,
    val autoBackupSource: BackupSource = BackupSource.GOOGLE_DRIVE,
)
