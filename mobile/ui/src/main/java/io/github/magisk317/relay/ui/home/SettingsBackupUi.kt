package io.github.magisk317.relay.ui.home

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.backup.RelayBackupManager
import io.github.magisk317.relay.core.R
import io.github.magisk317.smscode.runtime.common.backup.BackupImportResult
import io.github.magisk317.smscode.runtime.common.backup.ImportResult
import io.github.magisk317.smscode.runtime.common.backup.ImportWarning

internal data class BackupSelection(
    val includeConfig: Boolean = true,
    val includeRules: Boolean = true,
    val includeRecords: Boolean = true,
    val includeDatabase: Boolean = true,
) {
    fun hasSelection(): Boolean {
        return includeConfig || includeRules || includeRecords || includeDatabase
    }
}

@Composable
internal fun BackupRestoreOptionsDialog(
    title: String,
    message: String,
    initialSelection: BackupSelection,
    warningMessage: String? = null,
    confirmEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (BackupSelection) -> Unit,
) {
    var includeConfig by remember(initialSelection) { mutableStateOf(initialSelection.includeConfig) }
    var includeRules by remember(initialSelection) { mutableStateOf(initialSelection.includeRules) }
    var includeRecords by remember(initialSelection) { mutableStateOf(initialSelection.includeRecords) }
    var includeDatabase by remember(initialSelection) { mutableStateOf(initialSelection.includeDatabase) }

    val selection = BackupSelection(
        includeConfig = includeConfig,
        includeRules = includeRules,
        includeRecords = includeRecords,
        includeDatabase = includeDatabase,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = message)
                BackupRestoreOptionRow(
                    label = stringResource(id = R.string.item_config),
                    checked = includeConfig,
                    onCheckedChange = { includeConfig = it },
                )
                BackupRestoreOptionRow(
                    label = stringResource(id = R.string.item_rules),
                    checked = includeRules,
                    onCheckedChange = { includeRules = it },
                )
                BackupRestoreOptionRow(
                    label = stringResource(id = R.string.item_records),
                    checked = includeRecords,
                    onCheckedChange = { includeRecords = it },
                )
                BackupRestoreOptionRow(
                    label = stringResource(id = R.string.item_database_with_note),
                    checked = includeDatabase,
                    onCheckedChange = { includeDatabase = it },
                )
                warningMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selection) },
                enabled = selection.hasSelection() && confirmEnabled,
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun BackupInspectionResultDialog(
    inspection: RelayBackupManager.BackupInspection,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.backup_success)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.backup_inspect_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = backupInspectionDialogMessage(
                        context = context,
                        inspection = inspection,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun BackupRestoreOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun backupResultMessage(
    context: Context,
    success: Boolean,
): String {
    return if (success) {
        context.getString(R.string.backup_success)
    } else {
        context.getString(R.string.backup_failed)
    }
}

internal fun backupInspectionDialogMessage(
    context: Context,
    inspection: RelayBackupManager.BackupInspection?,
): String {
    if (inspection == null) {
        return context.getString(R.string.backup_inspect_unavailable)
    }
    return inspectionSummaryMessage(context, inspection)
}

internal fun restoreInspectionMessage(
    context: Context,
    inspection: RelayBackupManager.BackupInspection?,
    loading: Boolean,
): String {
    if (loading) {
        return buildString {
            append(context.getString(R.string.restore_warning_msg))
            append('\n')
            append('\n')
            append(context.getString(R.string.backup_inspect_loading))
        }
    }
    if (inspection == null) {
        return buildString {
            append(context.getString(R.string.restore_warning_msg))
            append('\n')
            append(context.getString(R.string.backup_inspect_unavailable))
        }
    }
    return buildString {
        append(context.getString(R.string.restore_warning_msg))
        append('\n')
        append('\n')
        append(inspectionSummaryMessage(context, inspection))
    }
}

private fun inspectionSummaryMessage(
    context: Context,
    inspection: RelayBackupManager.BackupInspection,
): String {
    val yesNoPayload = context.getString(if (inspection.payloadReadable) R.string.yes else R.string.no)
    val yesNoDatabase = context.getString(if (inspection.databasePresent) R.string.yes else R.string.no)
    return buildString {
        append(context.getString(R.string.backup_inspect_payload_readable, yesNoPayload))
        append('\n')
        append(
            context.getString(
                R.string.backup_inspect_payload_counts,
                inspection.payloadRules,
                inspection.payloadPreferences,
                inspection.payloadRecords,
            ),
        )
        append('\n')
        append(context.getString(R.string.backup_inspect_database_present, yesNoDatabase))
        append('\n')
        append(
            context.getString(
                R.string.backup_inspect_sender_counts,
                inspection.senderCount,
                inspection.blankSenderConfigs,
                inspection.degradedSenderConfigs,
            ),
        )
        if (!inspection.databasePresent) {
            append('\n')
            append(context.getString(R.string.backup_inspect_missing_database_hint))
        }
        if (inspection.degradedSenderConfigs > 0) {
            append('\n')
            append(context.getString(R.string.backup_inspect_degraded_sender_hint))
        }
    }
}

internal fun restoreResultMessage(
    context: Context,
    result: BackupImportResult,
): String {
    val base = when (result.result) {
        ImportResult.SUCCESS -> context.getString(R.string.restore_success)
        ImportResult.VERSION_MISSED -> context.getString(R.string.import_failed_version_missed)
        ImportResult.VERSION_UNKNOWN -> context.getString(R.string.import_failed_version_unknown)
        ImportResult.VERSION_TOO_NEW -> context.getString(R.string.import_failed_version_too_new)
        ImportResult.VERSION_TOO_OLD -> context.getString(R.string.import_failed_version_too_old)
        ImportResult.BACKUP_INVALID -> context.getString(R.string.import_failed_backup_invalid)
        ImportResult.READ_FAILED -> context.getString(R.string.import_failed_read_error)
    }
    val warning = when (result.warning) {
        ImportWarning.APP_VERSION_MISMATCH -> context.getString(R.string.import_warning_app_version_mismatch)
        null -> null
    }
    return if (result.result == ImportResult.SUCCESS && !warning.isNullOrBlank()) {
        "$base · $warning"
    } else {
        base
    }
}
