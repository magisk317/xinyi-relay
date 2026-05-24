@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.android.diagnostics.LogBundleExporter
import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.backup.RelayBackupManager
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.contract.settings.DiagnosticsSettingsSnapshot
import io.github.magisk317.relay.contract.settings.DiagnosticsSettingsUpdate
import io.github.magisk317.relay.contract.settings.GeneralSettingsSnapshot
import io.github.magisk317.relay.contract.settings.GeneralSettingsUpdate
import io.github.magisk317.relay.contract.settings.RelaySettingsSnapshot
import io.github.magisk317.relay.contract.settings.RelaySettingsUpdate
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.settings.VerificationSettingsSnapshot
import io.github.magisk317.relay.contract.settings.VerificationSettingsUpdate
import io.github.magisk317.relay.ui.common.filterNonNegativeIntegerInput
import io.github.magisk317.relay.ui.common.parseIntAtLeastInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onOpenVerification: () -> Unit,
    onOpenAdvancedRelay: () -> Unit,
    onOpenAccount: () -> Unit = {},
    onOpenCloudBackup: () -> Unit = {},
    onOpenDonate: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val repository: SettingsPreferencesRepository = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activityOwner = context as? ComponentActivity
    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)
    val snackbarHostState = remember { SnackbarHostState() }
    val notifySaved = {
        scope.launch {
            snackbarHostState.showSnackbar(savedSnackbarText)
        }
    }
    val settingsViewModel = rememberSharedSettingsViewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    val themeState by settingsViewModel.themeState.collectAsStateWithLifecycle()
    val languageState by settingsViewModel.languageState.collectAsStateWithLifecycle()
    var general by remember { mutableStateOf<GeneralSettingsSnapshot?>(null) }
    var verification by remember { mutableStateOf<VerificationSettingsSnapshot?>(null) }
    var relay by remember { mutableStateOf<RelaySettingsSnapshot?>(null) }
    var diagnostics by remember { mutableStateOf<DiagnosticsSettingsSnapshot?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showRuntimeLogRetentionDialog by remember { mutableStateOf(false) }
    var showRuntimeLogInfoDialog by remember { mutableStateOf(false) }
    var runtimeLogDialogData by remember { mutableStateOf<RuntimeLogDialogData?>(null) }
    var showRuntimeLogFullScreenPreview by remember { mutableStateOf(false) }
    var runtimeLogWrapLines by rememberSaveable { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var pendingBackupSelection by remember { mutableStateOf<BackupSelection?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupInspectionDialog by remember { mutableStateOf<RelayBackupManager.BackupInspection?>(null) }
    var restoreInspection by remember { mutableStateOf<RelayBackupManager.BackupInspection?>(null) }
    var restoreInspectionLoading by remember { mutableStateOf(false) }
    var themeDialogInitialMode by remember { mutableStateOf(0) }
    var themeDialogSelectedMode by remember { mutableStateOf(0) }
    var languageDialogInitialTag by remember { mutableStateOf("") }
    var languageDialogSelectedTag by remember { mutableStateOf("") }
    var expandGeneral by rememberSaveable { mutableStateOf(false) }
    var expandFeatures by rememberSaveable { mutableStateOf(false) }
    var expandSupport by rememberSaveable { mutableStateOf(false) }
    var expandBackupRestore by rememberSaveable { mutableStateOf(false) }
    var expandOthers by rememberSaveable { mutableStateOf(false) }

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

    fun shareRuntimeLogBundle() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LogBundleExporter.buildLogBundle(context)
            }
            val file = result.file
            if (file == null) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.runtime_log_export_failed, result.details),
                )
                return@launch
            }
            runCatching {
                LogBundleExporter.shareLogBundle(context, file)
            }.onFailure {
                snackbarHostState.showSnackbar(
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

    LaunchedEffect(Unit) {
        general = repository.getGeneralSettings()
        verification = repository.getVerificationSettings()
        relay = repository.getRelaySettings()
        diagnostics = repository.getDiagnosticsSettings()
    }

    LaunchedEffect(general?.accordionMode) {
        val accordionEnabled = general?.accordionMode ?: return@LaunchedEffect
        val expanded = !accordionEnabled
        expandGeneral = expanded
        expandFeatures = expanded
        expandSupport = expanded
        expandBackupRestore = expanded
        expandOthers = expanded
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
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.tab_settings)) },
                navigationIcon = if (onBack != null) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                } else {
                    {}
                },
            )
        },
        snackbarHost = {
            io.github.magisk317.relay.ui.common.DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { padding ->
        val generalSnapshot = general ?: return@Scaffold
        val verificationSnapshot = verification ?: return@Scaffold
        val relaySnapshot = relay ?: return@Scaffold
        val diagnosticsSnapshot = diagnostics ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
        ) {
            Spacer(modifier = Modifier.height(Const.PADDING_SMALL.dp))
            SettingsGeneralSection(
                general = generalSnapshot,
                themeSummary = themeModeSummary(themeState.mode),
                languageSummary = languageSummary(languageState.languageTag),
                expanded = expandGeneral,
                onExpandedChange = { expandGeneral = !expandGeneral },
                onModuleEnabledChange = { enabled ->
                    scope.launch {
                        general = repository.updateGeneralSettings(GeneralSettingsUpdate(moduleEnabled = enabled))
                        notifySaved()
                    }
                },
                onAccordionModeChange = { enabled ->
                    scope.launch {
                        general = repository.updateGeneralSettings(GeneralSettingsUpdate(accordionMode = enabled))
                        notifySaved()
                    }
                },
                onThemeClick = {
                    themeDialogInitialMode = themeState.mode
                    themeDialogSelectedMode = themeState.mode
                    showThemeDialog = true
                },
                onLanguageClick = {
                    languageDialogInitialTag = languageState.languageTag
                    languageDialogSelectedTag = languageState.languageTag
                    showLanguageDialog = true
                },
            )
            SettingsFeaturesSection(
                verification = verificationSnapshot,
                relay = relaySnapshot,
                expanded = expandFeatures,
                onExpandedChange = { expandFeatures = !expandFeatures },
                onOpenVerification = onOpenVerification,
                onVerificationEnabledChange = { enabled ->
                    scope.launch {
                        verification = repository.updateVerificationSettings(
                            VerificationSettingsUpdate(verificationFeaturesEnabled = enabled),
                        )
                        notifySaved()
                    }
                },
                onOpenAdvancedRelay = onOpenAdvancedRelay,
                onRelayEnabledChange = { enabled ->
                    scope.launch {
                        relay = repository.updateRelaySettings(RelaySettingsUpdate(relayFeaturesEnabled = enabled))
                        notifySaved()
                    }
                },
            )
            SettingsSupportSection(
                expanded = expandSupport,
                onExpandedChange = { expandSupport = !expandSupport },
                onOpenAccount = onOpenAccount,
                onOpenCloudBackup = onOpenCloudBackup,
                onOpenDonate = onOpenDonate,
            )
            SettingsBackupRestoreSection(
                expanded = expandBackupRestore,
                onExpandedChange = { expandBackupRestore = !expandBackupRestore },
                onBackupClick = { showBackupDialog = true },
                onRestoreClick = {
                    restoreDocumentLauncher.launch(RelayBackupManager.getImportRuleListSAFIntent(context))
                },
            )
            SettingsDiagnosticsSection(
                diagnostics = diagnosticsSnapshot,
                expanded = expandOthers,
                onExpandedChange = { expandOthers = !expandOthers },
                onRuntimeLogTitleClick = {
                    runtimeLogDialogData = null
                    showRuntimeLogInfoDialog = true
                },
                onVerboseLogModeChange = { enabled ->
                    scope.launch {
                        diagnostics = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(verboseLogMode = enabled),
                        )
                        RuntimeLogStore.setEnabled(enabled)
                        XLog.setLogLevel(if (enabled) Log.VERBOSE else io.github.magisk317.relay.android.BuildConfig.LOG_LEVEL)
                        notifySaved()
                    }
                },
                onSensitiveDebugLogModeChange = { enabled ->
                    scope.launch {
                        diagnostics = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(sensitiveDebugLogMode = enabled),
                        )
                        SensitiveLogPolicy.setEnabled(enabled)
                        notifySaved()
                    }
                },
                onRuntimeLogRetentionClick = { showRuntimeLogRetentionDialog = true },
                onAutoUpdateOnStartChange = { enabled ->
                    scope.launch {
                        diagnostics = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(autoUpdateOnStart = enabled),
                        )
                        notifySaved()
                    }
                },
                onAutoUpdateWifiOnlyChange = { enabled ->
                    scope.launch {
                        diagnostics = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(autoUpdateWifiOnly = enabled),
                        )
                        notifySaved()
                    }
                },
                onAnalyticsEnabledChange = { enabled ->
                    scope.launch {
                        diagnostics = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(analyticsEnabled = enabled),
                        )
                        notifySaved()
                    }
                },
            )
            Spacer(modifier = Modifier.height(Const.PADDING_SMALL.dp))
        }
    }

    if (showThemeDialog) {
        val themeOptions = listOf(
            stringResource(id = R.string.theme_follow_system),
            stringResource(id = R.string.theme_light),
            stringResource(id = R.string.theme_dark),
            stringResource(id = R.string.theme_black),
        )
        SingleChoiceDialog(
            title = stringResource(id = R.string.pref_choose_theme_title),
            options = themeOptions,
            selectedIndex = themeDialogSelectedMode.coerceIn(themeOptions.indices),
            onDismiss = {
                settingsViewModel.previewThemeMode(themeDialogInitialMode)
                showThemeDialog = false
            },
            onSelectionChange = { index ->
                themeDialogSelectedMode = index
                settingsViewModel.previewThemeMode(index)
            },
        ) { index ->
            showThemeDialog = false
            themeDialogSelectedMode = index
            settingsViewModel.persistThemeMode(index)
            notifySaved()
        }
    }
    if (showLanguageDialog) {
        val languageTags = listOf("", "zh-CN", "zh-TW", "en")
        val languageOptions = listOf(
            stringResource(id = R.string.language_follow_system),
            stringResource(id = R.string.language_zh_cn),
            stringResource(id = R.string.language_zh_tw),
            stringResource(id = R.string.language_en),
        )
        SingleChoiceDialog(
            title = stringResource(id = R.string.pref_language_title),
            options = languageOptions,
            selectedIndex = languageTags.indexOf(languageDialogSelectedTag).takeIf { it >= 0 } ?: 0,
            onDismiss = {
                settingsViewModel.previewLanguageTag(languageDialogInitialTag)
                showLanguageDialog = false
            },
            onSelectionChange = { index ->
                languageDialogSelectedTag = languageTags[index]
                settingsViewModel.previewLanguageTag(languageTags[index])
            },
        ) { index ->
            showLanguageDialog = false
            languageDialogSelectedTag = languageTags[index]
            settingsViewModel.persistLanguageTag(languageTags[index])
            notifySaved()
        }
    }
    if (showRuntimeLogInfoDialog) {
        LaunchedEffect(showRuntimeLogInfoDialog) {
            runtimeLogDialogData = withContext(Dispatchers.IO) {
                loadRuntimeLogDialogData(runtimeLogDialogData?.selectedFileName)
            }
        }
        val dialogData = runtimeLogDialogData
        RuntimeLogInfoDialog(
            data = dialogData,
            onDismiss = { showRuntimeLogInfoDialog = false },
            onShare = { shareRuntimeLogBundle() },
            onSelectFile = { fileName -> loadRuntimeLogDialog(fileName) },
            onOpenPreview = { showRuntimeLogFullScreenPreview = true },
            onClear = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        LogBundleExporter.clearLogFolders(context)
                    }
                    runtimeLogDialogData = withContext(Dispatchers.IO) {
                        loadRuntimeLogDialogData()
                    }
                    snackbarHostState.showSnackbar(
                        if (result.success) {
                            context.getString(R.string.runtime_log_cleared)
                        } else {
                            context.getString(R.string.runtime_log_clear_partial_failed, result.details)
                        },
                    )
                }
            },
        )
        val content = dialogData?.content
        if (showRuntimeLogFullScreenPreview && content != null) {
            RuntimeLogFullScreenPreviewDialog(
                fileName = content.name,
                text = dialogData.formattedPreview,
                wrapLines = runtimeLogWrapLines,
                onWrapLinesChange = { runtimeLogWrapLines = it },
                onDismiss = { showRuntimeLogFullScreenPreview = false },
            )
        }
    }
    val currentDiagnostics = diagnostics
    if (showRuntimeLogRetentionDialog && currentDiagnostics != null) {
        val runtimeLogRetentionDaysError = stringResource(id = R.string.pref_runtime_log_retention_days_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_runtime_log_retention_days_title),
            initialValue = currentDiagnostics.runtimeLogRetentionDays.toString(),
            onDismiss = { showRuntimeLogRetentionDialog = false },
            supportingText = stringResource(id = R.string.pref_runtime_log_retention_days_hint),
            validator = {
                if (parseIntAtLeastInput(it, PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN) != null) {
                    null
                } else {
                    runtimeLogRetentionDaysError
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            inputFilter = ::filterNonNegativeIntegerInput,
        ) { updated ->
            showRuntimeLogRetentionDialog = false
            scope.launch {
                diagnostics = repository.updateDiagnosticsSettings(
                    DiagnosticsSettingsUpdate(
                        runtimeLogRetentionDays = parseIntAtLeastInput(
                            updated,
                            PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN,
                        ) ?: PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN,
                    ),
                )
                notifySaved()
            }
        }
    }
    if (showBackupDialog) {
        BackupRestoreOptionsDialog(
            title = stringResource(id = R.string.dialog_backup_title),
            message = stringResource(id = R.string.dialog_backup_msg),
            initialSelection = BackupSelection(),
            onDismiss = { showBackupDialog = false },
        ) { selection ->
            showBackupDialog = false
            pendingBackupSelection = selection
            backupDocumentLauncher.launch(
                RelayBackupManager.getExportRuleListSAFIntent(
                    context = context,
                    includeDatabase = selection.includeDatabase,
                ),
            )
        }
    }
    if (showRestoreDialog && pendingRestoreUri != null) {
        BackupRestoreOptionsDialog(
            title = stringResource(id = R.string.dialog_restore_title),
            message = stringResource(id = R.string.dialog_restore_msg),
            initialSelection = BackupSelection(),
            warningMessage = restoreInspectionMessage(
                context = context,
                inspection = restoreInspection,
                loading = restoreInspectionLoading,
            ),
            confirmEnabled = !restoreInspectionLoading,
            onDismiss = {
                showRestoreDialog = false
                pendingRestoreUri = null
                restoreInspection = null
                restoreInspectionLoading = false
            },
        ) { selection ->
            val restoreUri = pendingRestoreUri ?: return@BackupRestoreOptionsDialog
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
        }
    }
    val backupInspectionState = backupInspectionDialog
    if (backupInspectionState != null) {
        AlertDialog(
            onDismissRequest = { backupInspectionDialog = null },
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
                            inspection = backupInspectionState,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { backupInspectionDialog = null }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun themeModeSummary(mode: Int): String {
    return when (mode) {
        1 -> stringResource(id = R.string.theme_light)
        2 -> stringResource(id = R.string.theme_dark)
        3 -> stringResource(id = R.string.theme_black)
        else -> stringResource(id = R.string.theme_follow_system)
    }
}

@Composable
private fun languageSummary(languageTag: String): String {
    return when (languageTag) {
        "zh-CN" -> stringResource(id = R.string.language_zh_cn)
        "zh-TW" -> stringResource(id = R.string.language_zh_tw)
        "en" -> stringResource(id = R.string.language_en)
        else -> stringResource(id = R.string.language_follow_system)
    }
}
