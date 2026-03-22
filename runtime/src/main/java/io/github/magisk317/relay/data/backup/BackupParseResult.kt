package io.github.magisk317.relay.data.backup

data class BackupParseResult(
    val schemaVersion: Int,
    val appVersion: String,
    val rules: List<BackupRule>,
    val preferences: Map<String, String?>?,
    val records: List<BackupSmsRecord>?,
)
