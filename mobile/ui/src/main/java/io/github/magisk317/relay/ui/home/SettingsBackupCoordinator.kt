package io.github.magisk317.relay.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.magisk317.relay.backup.RelayBackupManager

internal data class SettingsBackupRestoreActions(
    val onBackupClick: () -> Unit,
    val onRestoreClick: () -> Unit,
)

@Composable
internal fun rememberSettingsBackupRestoreActions(
    settingsViewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
): SettingsBackupRestoreActions {
    val context = LocalContext.current
    val activityOwner = context as? ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var pendingBackupSelection by remember { mutableStateOf<BackupSelection?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupInspectionDialog by remember { mutableStateOf<RelayBackupManager.BackupInspection?>(null) }
    var restoreInspection by remember { mutableStateOf<RelayBackupManager.BackupInspection?>(null) }
    var restoreInspectionLoading by remember { mutableStateOf(false) }

    val backupDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val selection = pendingBackupSelection
        pendingBackupSelection = null
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK || selection == null) return@rememberLauncherForActivityResult
        settingsViewModel.performBackup(
            uri = uri,
            includeConfig = selection.includeConfig,
            includeRules = selection.includeRules,
            includeRecords = selection.includeRecords,
            includeDatabase = selection.includeDatabase,
        )
    }

    val restoreDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        pendingRestoreUri = uri
        showRestoreDialog = true
    }

    LaunchedEffect(activityOwner?.intent?.data) {
        val backupUri = activityOwner?.intent?.data ?: return@LaunchedEffect
        pendingRestoreUri = backupUri
        restoreInspection = null
        restoreInspectionLoading = true
        showRestoreDialog = true
        activityOwner.intent = Intent(activityOwner.intent).apply {
            data = null
        }
    }

    LaunchedEffect(showRestoreDialog, pendingRestoreUri) {
        val restoreUri = pendingRestoreUri
        if (!showRestoreDialog || restoreUri == null) {
            restoreInspection = null
            restoreInspectionLoading = false
            return@LaunchedEffect
        }
        restoreInspection = null
        restoreInspectionLoading = true
        restoreInspection = settingsViewModel.inspectBackup(restoreUri)
        restoreInspectionLoading = false
    }

    LaunchedEffect(lifecycleOwner, settingsViewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            settingsViewModel.eventsFlow.collect { event ->
                when (event) {
                    is SettingsEvent.BackupResultEvent -> {
                        if (event.success) {
                            backupInspectionDialog = event.inspection
                        } else {
                            snackbarHostState.showSnackbar(backupResultMessage(context, event.success))
                        }
                    }

                    is SettingsEvent.RestoreResultEvent -> {
                        snackbarHostState.showSnackbar(restoreResultMessage(context, event.result))
                    }

                    is SettingsEvent.ImportDialogConfirm -> {
                        pendingRestoreUri = event.uri
                        restoreInspection = null
                        restoreInspectionLoading = true
                        showRestoreDialog = true
                    }

                    else -> Unit
                }
            }
        }
    }

    BackupRestoreDialogHost(
        showBackupDialog = showBackupDialog,
        showRestoreDialog = showRestoreDialog,
        pendingRestoreUri = pendingRestoreUri,
        restoreInspection = restoreInspection,
        restoreInspectionLoading = restoreInspectionLoading,
        onDismissBackup = { showBackupDialog = false },
        onConfirmBackup = { selection ->
            showBackupDialog = false
            pendingBackupSelection = selection
            backupDocumentLauncher.launch(
                RelayBackupManager.getExportRuleListSAFIntent(
                    context = context,
                    includeDatabase = selection.includeDatabase,
                ),
            )
        },
        onDismissRestore = {
            showRestoreDialog = false
            pendingRestoreUri = null
            restoreInspection = null
            restoreInspectionLoading = false
        },
        onConfirmRestore = { restoreUri, selection ->
            showRestoreDialog = false
            pendingRestoreUri = null
            restoreInspection = null
            restoreInspectionLoading = false
            settingsViewModel.performRestore(
                uri = restoreUri,
                restoreConfig = selection.includeConfig,
                restoreRules = selection.includeRules,
                restoreRecords = selection.includeRecords,
                restoreDatabase = selection.includeDatabase,
            )
        },
    )

    val backupInspectionState = backupInspectionDialog
    if (backupInspectionState != null) {
        BackupInspectionResultDialog(
            inspection = backupInspectionState,
            onDismiss = { backupInspectionDialog = null },
        )
    }

    return remember(context, restoreDocumentLauncher) {
        SettingsBackupRestoreActions(
            onBackupClick = { showBackupDialog = true },
            onRestoreClick = {
                restoreDocumentLauncher.launch(RelayBackupManager.getImportRuleListSAFIntent(context))
            },
        )
    }
}
