package io.github.magisk317.relay.feature.backup

data class BackupImportResult(
    val result: ImportResult,
    val rules: List<BackupRule> = emptyList(),
    val preferences: Map<String, String?>? = null,
    val records: List<BackupSmsRecord>? = null,
    val warning: ImportWarning? = null,
)
