package com.github.magisk317.smscode.feature.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
@Serializable
data class BackupPayload(
    @SerialName(BackupConst.KEY_VERSION)
    val version: Int = BackupConst.BACKUP_VERSION,
    @SerialName(BackupConst.KEY_SCHEMA_VERSION)
    val schemaVersion: Int = BackupConst.BACKUP_VERSION,
    @SerialName(BackupConst.KEY_APP_VERSION)
    val appVersion: String = "",
    @SerialName(BackupConst.KEY_RULES)
    val rules: List<BackupRule> = emptyList(),
    @SerialName(BackupConst.KEY_PREFERENCES)
    val preferences: Map<String, String?>? = null,
    @SerialName(BackupConst.KEY_RECORDS)
    val records: List<BackupSmsRecord>? = null,
)
