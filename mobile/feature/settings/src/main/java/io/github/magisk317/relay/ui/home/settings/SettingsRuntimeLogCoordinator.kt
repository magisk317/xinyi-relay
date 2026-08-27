package io.github.magisk317.relay.ui.home.settings

import io.github.magisk317.relay.android.diagnostics.RuntimeDiagnosticsBridge
import io.github.magisk317.uikit.common.showLatestSnackbar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import io.github.magisk317.uikit.surface.ConfirmActionDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.magisk317.smscode.runtime.common.diagnostics.LogBundleExporter
import io.github.magisk317.xposed.diagnostics.DiagnosticExportMode
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

    val saveRuntimeLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination ->
        if (destination == null) return@rememberLauncherForActivityResult
        scope.launch {
            val failure = withContext(Dispatchers.IO) {
                RuntimeDiagnosticsBridge.ensureInstalled()
                val bundle = LogBundleExporter.buildLogBundle(
                    context = context,
                    mode = DiagnosticExportMode.fromDebugLogging(
                        repository.getDiagnosticsSettings().verboseLogMode,
                    ),
                )
                val file = bundle.file ?: return@withContext bundle.details
                context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: return@withContext "log_export_destination_open_failed"
                ""
            }
            if (failure.isNotBlank()) {
                snackbarHostState.showLatestSnackbar(
                    context.getString(R.string.runtime_log_export_failed, failure),
                )
            }
        }
    }

    fun saveLog() {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
            .format(java.util.Date())
        saveRuntimeLogLauncher.launch("xinyi_logs_$timestamp.zip")
    }

    fun clearLog() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RuntimeDiagnosticsBridge.ensureInstalled()
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
        ConfirmActionDialog(
            title = stringResource(R.string.runtime_log_clear_confirm_title),
            message = stringResource(R.string.runtime_log_clear_confirm_message),
            confirmText = stringResource(R.string.action_clear),
            cancelText = stringResource(R.string.cancel),
            onDismissRequest = { showClearConfirmDialog = false },
            onConfirm = {
                showClearConfirmDialog = false
                clearLog()
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
            onRuntimeLogTitleClick = { saveLog() },
            onRuntimeLogRetentionClick = { showRetentionDialog = true },
            onClearLog = { showClearConfirmDialog = true },
        )
    }
}
