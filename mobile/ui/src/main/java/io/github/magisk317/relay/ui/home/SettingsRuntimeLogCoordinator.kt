package io.github.magisk317.relay.ui.home

import io.github.magisk317.relay.ui.common.showLatestSnackbar

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
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
    var showRuntimeLogRetentionDialog by remember { mutableStateOf(false) }
    var showRuntimeLogInfoDialog by remember { mutableStateOf(false) }
    var runtimeLogDialogData by remember { mutableStateOf<RuntimeLogDialogData?>(null) }
    var showRuntimeLogFullScreenPreview by remember { mutableStateOf(false) }
    var runtimeLogWrapLines by rememberSaveable { mutableStateOf(false) }

    fun shareRuntimeLogBundle() {
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

    fun loadRuntimeLogDialog(selectedFileName: String? = null) {
        scope.launch {
            runtimeLogDialogData = withContext(Dispatchers.IO) {
                loadRuntimeLogDialogData(selectedFileName)
            }
        }
    }

    fun clearRuntimeLogFolders() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LogBundleExporter.clearLogFolders(context)
            }
            runtimeLogDialogData = withContext(Dispatchers.IO) {
                loadRuntimeLogDialogData()
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

    RuntimeLogDialogHost(
        showInfoDialog = showRuntimeLogInfoDialog,
        data = runtimeLogDialogData,
        showFullScreenPreview = showRuntimeLogFullScreenPreview,
        wrapLines = runtimeLogWrapLines,
        onLoadData = { selectedFileName -> loadRuntimeLogDialog(selectedFileName) },
        onDismissInfo = { showRuntimeLogInfoDialog = false },
        onShare = { shareRuntimeLogBundle() },
        onSelectFile = { fileName -> loadRuntimeLogDialog(fileName) },
        onOpenPreview = { showRuntimeLogFullScreenPreview = true },
        onClear = { clearRuntimeLogFolders() },
        onWrapLinesChange = { runtimeLogWrapLines = it },
        onDismissPreview = { showRuntimeLogFullScreenPreview = false },
    )

    val currentDiagnostics = diagnostics
    if (showRuntimeLogRetentionDialog && currentDiagnostics != null) {
        SettingsRuntimeLogRetentionDialog(
            retentionDays = currentDiagnostics.runtimeLogRetentionDays,
            onDismiss = { showRuntimeLogRetentionDialog = false },
            onConfirm = { updated ->
                showRuntimeLogRetentionDialog = false
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
            onRuntimeLogTitleClick = {
                runtimeLogDialogData = null
                showRuntimeLogInfoDialog = true
            },
            onRuntimeLogRetentionClick = { showRuntimeLogRetentionDialog = true },
        )
    }
}
