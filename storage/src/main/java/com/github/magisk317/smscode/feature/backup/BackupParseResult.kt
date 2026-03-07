package com.github.magisk317.smscode.feature.backup

data class BackupParseResult(
    val schemaVersion: Int,
    val appVersion: String,
    val rules: List<BackupRule>,
    val preferences: Map<String, String?>?,
    val records: List<BackupSmsRecord>?,
)
