package io.github.magisk317.relay.ui.home.settings

import io.github.magisk317.uikit.common.showLatestSnackbar

import android.app.Activity
import android.net.Uri
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

internal data class SettingsBackupDialogEventBindings(
    val backupInspectionEventId: Long? = null,
    val restoreEventId: Long? = null,
) {
    fun needsBackupInspectionInitialization(eventId: Long): Boolean =
        backupInspectionEventId != eventId

    fun needsRestoreInitialization(eventId: Long): Boolean = restoreEventId != eventId

    fun bindBackupInspection(eventId: Long): SettingsBackupDialogEventBindings =
        copy(backupInspectionEventId = eventId)

    fun bindRestore(eventId: Long): SettingsBackupDialogEventBindings =
        copy(restoreEventId = eventId)

    fun clearBackupInspection(): SettingsBackupDialogEventBindings =
        copy(backupInspectionEventId = null)

    fun clearRestore(): SettingsBackupDialogEventBindings = copy(restoreEventId = null)
}

@Composable
internal fun rememberSettingsBackupRestoreActions(
    settingsViewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigateToCloudBackup: (BackupSourceType, Boolean) -> Unit,
    isActive: Boolean = true,
): SettingsBackupRestoreActions {
    val workPolicy = settingsPageWorkPolicy(isActive)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showBackupSourceDialog by remember { mutableStateOf(false) }
    var showRestoreSourceDialog by remember { mutableStateOf(false) }
    var pendingBackupSelection by remember { mutableStateOf<BackupSelection?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupInspectionDialog by remember { mutableStateOf<RelayBackupManager.BackupInspection?>(null) }
    var restoreInspection by remember { mutableStateOf<RelayBackupManager.BackupInspection?>(null) }
    var restoreInspectionLoading by remember { mutableStateOf(false) }
    var dialogEventBindings by remember { mutableStateOf(SettingsBackupDialogEventBindings()) }

    fun acknowledgeBackupInspectionEvent() {
        val eventId = dialogEventBindings.backupInspectionEventId
        dialogEventBindings = dialogEventBindings.clearBackupInspection()
        eventId?.let(settingsViewModel::acknowledgeBackupEvent)
    }

    fun acknowledgeRestoreDialogEvent() {
        val eventId = dialogEventBindings.restoreEventId
        dialogEventBindings = dialogEventBindings.clearRestore()
        eventId?.let(settingsViewModel::acknowledgeBackupEvent)
    }

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

    LaunchedEffect(workPolicy.inspectBackup, showRestoreDialog, pendingRestoreUri) {
        if (!workPolicy.inspectBackup) {
            restoreInspectionLoading = false
            return@LaunchedEffect
        }
        val restoreUri = pendingRestoreUri
        if (!showRestoreDialog || restoreUri == null) {
            restoreInspection = null
            restoreInspectionLoading = false
            return@LaunchedEffect
        }
        restoreInspection = null
        restoreInspectionLoading = true
        try {
            restoreInspection = settingsViewModel.inspectBackup(restoreUri)
        } finally {
            restoreInspectionLoading = false
        }
    }

    LaunchedEffect(lifecycleOwner, settingsViewModel, workPolicy.collectBackupEvents) {
        if (!workPolicy.collectBackupEvents) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            settingsViewModel.backupEventsFlow.collect { pendingEvents ->
                val pending = pendingEvents.firstOrNull() ?: return@collect
                when (val event = pending.event) {
                    is SettingsBackupEvent.BackupResult -> {
                        if (event.success && event.inspection != null) {
                            if (dialogEventBindings.needsBackupInspectionInitialization(pending.id)) {
                                backupInspectionDialog = event.inspection
                                dialogEventBindings = dialogEventBindings.bindBackupInspection(pending.id)
                            }
                            return@collect
                        } else if (event.success) {
                            // Export succeeded even when the optional inspection could not be
                            // read; report success through the durable snackbar path.
                            snackbarHostState.showLatestSnackbar(backupResultMessage(context, true))
                        } else {
                            snackbarHostState.showLatestSnackbar(backupResultMessage(context, event.success))
                        }
                    }

                    is SettingsBackupEvent.RestoreResult -> {
                        snackbarHostState.showLatestSnackbar(restoreResultMessage(context, event.result))
                    }

                    is SettingsBackupEvent.ImportDialogConfirm -> {
                        if (dialogEventBindings.needsRestoreInitialization(pending.id)) {
                            pendingRestoreUri = Uri.parse(event.uri)
                            restoreInspection = null
                            restoreInspectionLoading = true
                            showRestoreDialog = true
                            dialogEventBindings = dialogEventBindings.bindRestore(pending.id)
                        }
                        return@collect
                    }
                }
                // Snackbar side effects stay pending until the suspend call completes. If
                // collection is cancelled while a snackbar is showing, the event is retried
                // when Settings becomes active again.
                settingsViewModel.acknowledgeBackupEvent(pending.id)
            }
        }
    }

    if (isActive) {
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
                acknowledgeRestoreDialogEvent()
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
                acknowledgeRestoreDialogEvent()
            },
        )
    }

    val backupInspectionState = backupInspectionDialog
    if (isActive && backupInspectionState != null) {
        BackupInspectionResultDialog(
            inspection = backupInspectionState,
            onDismiss = {
                backupInspectionDialog = null
                acknowledgeBackupInspectionEvent()
            },
        )
    }

    if (isActive && showBackupSourceDialog) {
        BackupSourceDialog(
            title = androidx.compose.ui.res.stringResource(id = io.github.magisk317.relay.core.R.string.dialog_backup_source_title),
            onDismiss = { showBackupSourceDialog = false },
            onSourceSelected = { source ->
                showBackupSourceDialog = false
                when (source) {
                    BackupSourceType.LOCAL -> showBackupDialog = true
                    BackupSourceType.GOOGLE_DRIVE, BackupSourceType.WEBDAV -> onNavigateToCloudBackup(source, false)
                }
            }
        )
    }

    if (isActive && showRestoreSourceDialog) {
        BackupSourceDialog(
            title = androidx.compose.ui.res.stringResource(id = io.github.magisk317.relay.core.R.string.dialog_restore_source_title),
            onDismiss = { showRestoreSourceDialog = false },
            onSourceSelected = { source ->
                showRestoreSourceDialog = false
                when (source) {
                    BackupSourceType.LOCAL -> restoreDocumentLauncher.launch(RelayBackupManager.getImportRuleListSAFIntent(context))
                    BackupSourceType.GOOGLE_DRIVE, BackupSourceType.WEBDAV -> onNavigateToCloudBackup(source, false)
                }
            }
        )
    }

    return remember(context, restoreDocumentLauncher, isActive) {
        SettingsBackupRestoreActions(
            onBackupClick = { if (isActive) showBackupSourceDialog = true },
            onRestoreClick = { if (isActive) showRestoreSourceDialog = true },
        )
    }
}
