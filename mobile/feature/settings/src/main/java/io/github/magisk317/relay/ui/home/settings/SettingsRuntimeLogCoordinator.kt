package io.github.magisk317.relay.ui.home.settings

import io.github.magisk317.uikit.common.showLatestSnackbar

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.magisk317.relay.android.diagnostics.LogBundleExporter
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.settings.DiagnosticsSettingsSnapshot
import io.github.magisk317.relay.contract.settings.DiagnosticsSettingsUpdate
import io.github.magisk317.relay.core.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SettingsRuntimeLogActions(
    val onRuntimeLogTitleClick: () -> Unit,
    val onRuntimeLogRetentionClick: () -> Unit,
    val onClearLog: () -> Unit,
)

@Composable
internal fun rememberSettingsRuntimeLogActions(
    diagnostics: DiagnosticsSettingsSnapshot?,
    repository: SettingsPreferencesRepository,
    snackbarHostState: SnackbarHostState,
    onDiagnosticsChanged: (DiagnosticsSettingsSnapshot) -> Unit,
    notifySaved: () -> Unit,
): SettingsRuntimeLogActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    fun shareLog() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LogBundleExporter.buildLogBundle(context)
            }
            val file = result.file
            if (file == null) {
                snackbarHostState.showLatestSnackbar(
                    context.getString(R.string.runtime_log_export_failed, result.details),
                )
                return@launch
            }
            runCatching {
                LogBundleExporter.shareLogBundle(context, file)
            }.onFailure {
                snackbarHostState.showLatestSnackbar(
                    context.getString(
                        R.string.runtime_log_share_failed,
                        it.message ?: it.javaClass.simpleName,
                    ),
                )
            }
        }
    }

    fun clearLog() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LogBundleExporter.clearLogFolders(context)
            }
            snackbarHostState.showLatestSnackbar(
                if (result.success) {
                    context.getString(R.string.runtime_log_cleared)
                } else {
                    context.getString(R.string.runtime_log_clear_partial_failed, result.details)
                },
            )
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(stringResource(R.string.runtime_log_clear_confirm_title)) },
            text = { Text(stringResource(R.string.runtime_log_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmDialog = false
                    clearLog()
                }) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showRetentionDialog && diagnostics != null) {
        SettingsRuntimeLogRetentionDialog(
            retentionDays = diagnostics.runtimeLogRetentionDays,
            onDismiss = { showRetentionDialog = false },
            onConfirm = { updated ->
                showRetentionDialog = false
                scope.launch {
                    onDiagnosticsChanged(
                        repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(runtimeLogRetentionDays = updated),
                        ),
                    )
                    notifySaved()
                }
            },
        )
    }

    return remember {
        SettingsRuntimeLogActions(
            onRuntimeLogTitleClick = { shareLog() },
            onRuntimeLogRetentionClick = { showRetentionDialog = true },
            onClearLog = { showClearConfirmDialog = true },
        )
    }
}
