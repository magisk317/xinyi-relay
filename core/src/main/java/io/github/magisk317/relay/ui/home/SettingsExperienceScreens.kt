@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.home

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.core.BuildConfig
import io.github.magisk317.relay.common.constant.Const
import io.github.magisk317.relay.common.constant.CodeNotificationOwner
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.common.utils.NotificationUtils
import io.github.magisk317.relay.diagnostics.LogBundleExporter
import io.github.magisk317.relay.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.data.repository.DiagnosticsSettingsSnapshot
import io.github.magisk317.relay.data.repository.DiagnosticsSettingsUpdate
import io.github.magisk317.relay.data.repository.GeneralSettingsSnapshot
import io.github.magisk317.relay.data.repository.GeneralSettingsUpdate
import io.github.magisk317.relay.data.repository.RecordSettingsSnapshot
import io.github.magisk317.relay.data.repository.RelaySettingsSnapshot
import io.github.magisk317.relay.data.repository.RelaySettingsUpdate
import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.data.repository.VerificationSettingsSnapshot
import io.github.magisk317.relay.data.repository.VerificationSettingsUpdate
import io.github.magisk317.relay.data.backup.BackupManager
import io.github.magisk317.relay.ui.common.SingleChoiceOptionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onOpenVerification: () -> Unit,
    onOpenAdvancedRelay: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val repository: SettingsRepository = koinInject()
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
    var showRuntimeLogDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var pendingBackupSelection by remember { mutableStateOf<BackupSelection?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var themeDialogInitialMode by remember { mutableStateOf(0) }
    var themeDialogSelectedMode by remember { mutableStateOf(0) }
    var languageDialogInitialTag by remember { mutableStateOf("") }
    var languageDialogSelectedTag by remember { mutableStateOf("") }
    var expandGeneral by rememberSaveable { mutableStateOf(false) }
    var expandFeatures by rememberSaveable { mutableStateOf(false) }
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
        expandBackupRestore = expanded
        expandOthers = expanded
    }

    LaunchedEffect(activityOwner?.intent?.data) {
        val backupUri = activityOwner?.intent?.data ?: return@LaunchedEffect
        pendingRestoreUri = backupUri
        showRestoreDialog = true
        activityOwner.intent = Intent(activityOwner.intent).apply {
            data = null
        }
    }

    LaunchedEffect(lifecycleOwner, settingsViewModel) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            settingsViewModel.eventsFlow.collect { event ->
                when (event) {
                    is SettingsEvent.BackupResultEvent -> {
                        snackbarHostState.showSnackbar(backupResultMessage(context, event.success))
                    }

                    is SettingsEvent.RestoreResultEvent -> {
                        snackbarHostState.showSnackbar(restoreResultMessage(context, event.result))
                    }

                    is SettingsEvent.ImportDialogConfirm -> {
                        pendingRestoreUri = event.uri
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
            SectionCard(
                title = stringResource(id = R.string.settings_group_general),
                sectionExpanded = expandGeneral,
                onExpandedChange = { expandGeneral = !expandGeneral },
                accordionMode = true,
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_enable_title),
                    summary = stringResource(id = R.string.pref_enable_summary),
                    checked = generalSnapshot.moduleEnabled,
                ) { enabled ->
                    scope.launch {
                        general = repository.updateGeneralSettings(GeneralSettingsUpdate(moduleEnabled = enabled))
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_settings_display_mode_title),
                    summary = stringResource(id = R.string.pref_settings_display_mode_summary),
                    checked = generalSnapshot.accordionMode,
                ) { enabled ->
                    scope.launch {
                        general = repository.updateGeneralSettings(GeneralSettingsUpdate(accordionMode = enabled))
                        notifySaved()
                    }
                }
                Item(
                    title = stringResource(id = R.string.pref_choose_theme_title),
                    summary = themeModeSummary(themeState.mode),
                ) {
                    themeDialogInitialMode = themeState.mode
                    themeDialogSelectedMode = themeState.mode
                    showThemeDialog = true
                }
                Item(
                    title = stringResource(id = R.string.pref_language_title),
                    summary = languageSummary(languageState.languageTag),
                ) {
                    languageDialogInitialTag = languageState.languageTag
                    languageDialogSelectedTag = languageState.languageTag
                    showLanguageDialog = true
                }
            }
            SectionCard(
                title = stringResource(id = R.string.settings_group_features),
                sectionExpanded = expandFeatures,
                onExpandedChange = { expandFeatures = !expandFeatures },
                accordionMode = true,
            ) {
                ActionSwitchItem(
                    title = stringResource(id = R.string.pref_verification_settings_title),
                    summary = stringResource(id = R.string.pref_verification_settings_summary),
                    checked = verificationSnapshot.verificationFeaturesEnabled,
                    onClick = onOpenVerification,
                ) { enabled ->
                    scope.launch {
                        verification = repository.updateVerificationSettings(
                            VerificationSettingsUpdate(verificationFeaturesEnabled = enabled),
                        )
                        notifySaved()
                    }
                }
                ActionSwitchItem(
                    title = stringResource(id = R.string.pref_relay_features_title),
                    summary = stringResource(id = R.string.pref_relay_features_summary),
                    checked = relaySnapshot.relayFeaturesEnabled,
                    onClick = onOpenAdvancedRelay,
                ) { enabled ->
                    scope.launch {
                        relay = repository.updateRelaySettings(RelaySettingsUpdate(relayFeaturesEnabled = enabled))
                        notifySaved()
                    }
                }
            }
            SectionCard(
                title = stringResource(id = R.string.pref_backup_restore_title),
                sectionExpanded = expandBackupRestore,
                onExpandedChange = { expandBackupRestore = !expandBackupRestore },
                accordionMode = true,
            ) {
                Item(
                    title = stringResource(id = R.string.pref_backup_title),
                    summary = stringResource(id = R.string.pref_backup_summary),
                ) {
                    showBackupDialog = true
                }
                Item(
                    title = stringResource(id = R.string.pref_restore_title),
                    summary = stringResource(id = R.string.pref_restore_summary),
                ) {
                    restoreDocumentLauncher.launch(BackupManager.getImportRuleListSAFIntent(context))
                }
            }
            SectionCard(
                title = stringResource(id = R.string.settings_group_others),
                sectionExpanded = expandOthers,
                onExpandedChange = { expandOthers = !expandOthers },
                accordionMode = true,
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_verbose_log_mode_title),
                    summary = stringResource(id = R.string.pref_verbose_log_mode_summary),
                    checked = diagnosticsSnapshot.verboseLogMode,
                    onTitleClick = {
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
                    },
                ) { enabled ->
                    scope.launch {
                        diagnostics = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(verboseLogMode = enabled),
                        )
                        RuntimeLogStore.setEnabled(enabled)
                        XLog.setLogLevel(if (enabled) Log.VERBOSE else io.github.magisk317.relay.runtime.BuildConfig.LOG_LEVEL)
                        notifySaved()
                    }
                }
                if (BuildConfig.DEBUG) {
                    StateSwitchItem(
                        title = stringResource(id = R.string.pref_sensitive_debug_log_mode_title),
                        summary = stringResource(id = R.string.pref_sensitive_debug_log_mode_summary),
                        checked = diagnosticsSnapshot.sensitiveDebugLogMode,
                    ) { enabled ->
                        scope.launch {
                            diagnostics = repository.updateDiagnosticsSettings(
                                DiagnosticsSettingsUpdate(sensitiveDebugLogMode = enabled),
                            )
                            SensitiveLogPolicy.setEnabled(enabled)
                            notifySaved()
                        }
                    }
                }
                Item(
                    title = stringResource(id = R.string.pref_runtime_log_file_size_title),
                    summary = stringResource(
                        id = R.string.pref_runtime_log_file_size_summary,
                        diagnosticsSnapshot.runtimeLogFileSizeMb,
                    ),
                ) { showRuntimeLogDialog = true }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_auto_update_on_start_title),
                    summary = stringResource(id = R.string.pref_auto_update_on_start_summary),
                    checked = diagnosticsSnapshot.autoUpdateOnStart,
                ) { enabled ->
                    scope.launch {
                        diagnostics = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(autoUpdateOnStart = enabled),
                        )
                        notifySaved()
                    }
                }
                if (diagnosticsSnapshot.autoUpdateOnStart) {
                    StateSwitchItem(
                        title = stringResource(id = R.string.pref_auto_update_wifi_only_title),
                        summary = stringResource(id = R.string.pref_auto_update_wifi_only_summary),
                        checked = diagnosticsSnapshot.autoUpdateWifiOnly,
                    ) { enabled ->
                        scope.launch {
                            diagnostics = repository.updateDiagnosticsSettings(
                                DiagnosticsSettingsUpdate(autoUpdateWifiOnly = enabled),
                            )
                            notifySaved()
                        }
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_enable_analytics_title),
                    summary = stringResource(id = R.string.pref_enable_analytics_summary),
                    checked = diagnosticsSnapshot.analyticsEnabled,
                ) { enabled ->
                    scope.launch {
                        diagnostics = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(analyticsEnabled = enabled),
                        )
                        notifySaved()
                    }
                }
            }
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
    val currentDiagnostics = diagnostics
    if (showRuntimeLogDialog && currentDiagnostics != null) {
        val runtimeLogFileSizeError = stringResource(id = R.string.pref_runtime_log_file_size_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_runtime_log_file_size_title),
            initialValue = currentDiagnostics.runtimeLogFileSizeMb.toString(),
            onDismiss = { showRuntimeLogDialog = false },
            supportingText = stringResource(id = R.string.pref_runtime_log_file_size_hint),
            validator = {
                parseIntAtLeast(it, PrefConst.RUNTIME_LOG_FILE_SIZE_MB_MIN)?.let { null }
                    ?: runtimeLogFileSizeError
            },
        ) { updated ->
            showRuntimeLogDialog = false
            scope.launch {
                diagnostics = repository.updateDiagnosticsSettings(
                    DiagnosticsSettingsUpdate(
                        runtimeLogFileSizeMb = parseIntAtLeast(
                            updated,
                            PrefConst.RUNTIME_LOG_FILE_SIZE_MB_MIN,
                        ) ?: PrefConst.RUNTIME_LOG_FILE_SIZE_MB_MIN,
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
                BackupManager.getExportRuleListSAFIntent(
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
            warningMessage = stringResource(id = R.string.restore_warning_msg),
            onDismiss = {
                showRestoreDialog = false
                pendingRestoreUri = null
            },
        ) { selection ->
            val restoreUri = pendingRestoreUri ?: return@BackupRestoreOptionsDialog
            showRestoreDialog = false
            pendingRestoreUri = null
            settingsViewModel.performRestore(
                uri = restoreUri,
                restoreConfig = selection.includeConfig,
                restoreRules = selection.includeRules,
                restoreRecords = selection.includeRecords,
                restoreDatabase = selection.includeDatabase,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun VerificationSettingsScreen(
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val repository: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activityOwner = context as? ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)
    val snackbarHostState = remember { SnackbarHostState() }
    val notifySaved = {
        scope.launch {
            snackbarHostState.showSnackbar(savedSnackbarText)
        }
    }
    val settingsViewModel = rememberSharedSettingsViewModel()
    val accordionMode = rememberPrefBoolean(PrefConst.KEY_SETTINGS_ACCORDION_MODE, true)
    var settings by remember { mutableStateOf<VerificationSettingsSnapshot?>(null) }
    var recordSettings by remember { mutableStateOf<RecordSettingsSnapshot?>(null) }
    var showDelayDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showKeywordsDialog by remember { mutableStateOf(false) }
    var showNotificationOwnerDialog by remember { mutableStateOf(false) }
    var pendingEnableNotification by remember { mutableStateOf(false) }
    var pendingNotificationOwnerPermissionSelection by remember { mutableStateOf<String?>(null) }
    var pendingNotificationPermissionEnable by remember { mutableStateOf(false) }
    var showSmsTestDialog by remember { mutableStateOf(false) }
    var smsTestInput by remember { mutableStateOf("") }
    var expandRelaySection by rememberSaveable { mutableStateOf(true) }
    var expandAutoInputSection by rememberSaveable { mutableStateOf(true) }
    var expandNotificationSection by rememberSaveable { mutableStateOf(true) }
    var expandExperimentalSection by rememberSaveable { mutableStateOf(true) }
    val supportsAccessibilityAutoInput = BuildConfig.ENABLE_ACCESSIBILITY_AUTO_INPUT
    var autoInputAccessibilityEnabled by remember {
        mutableStateOf(
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context),
        )
    }
    var autoInputAccessibilityListed by remember {
        mutableStateOf(
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceListed(context),
        )
    }

    suspend fun persistNotificationOwnerSelection(owner: String, enableNotification: Boolean) {
        settings = repository.updateVerificationSettings(
            VerificationSettingsUpdate(
                notificationOwner = owner,
                showCodeNotification = if (enableNotification) true else null,
            ),
        )
        notifySaved()
    }

    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        scope.launch {
            if (
                pendingNotificationOwnerPermissionSelection == CodeNotificationOwner.APP &&
                NotificationUtils.hasPostNotificationsPermission(context)
            ) {
                val enableNotification = pendingNotificationPermissionEnable
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                persistNotificationOwnerSelection(
                    owner = CodeNotificationOwner.APP,
                    enableNotification = enableNotification,
                )
            } else if (pendingNotificationOwnerPermissionSelection == CodeNotificationOwner.APP) {
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                snackbarHostState.showSnackbar(
                    context.getString(R.string.pref_code_notification_owner_permission_denied),
                )
            }
        }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        val fallbackIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        if (activityOwner != null) {
            runCatching {
                notificationSettingsLauncher.launch(intent)
            }.recoverCatching {
                notificationSettingsLauncher.launch(fallbackIntent)
            }.onFailure {
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.pref_code_notification_owner_permission_denied),
                    )
                }
            }
            return
        }
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.recoverCatching {
            context.startActivity(fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            pendingNotificationOwnerPermissionSelection = null
            pendingNotificationPermissionEnable = false
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.pref_code_notification_owner_permission_denied),
                )
            }
        }
    }

    val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingNotificationOwnerPermissionSelection == CodeNotificationOwner.APP) {
            scope.launch {
                val enableNotification = pendingNotificationPermissionEnable
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                persistNotificationOwnerSelection(
                    owner = CodeNotificationOwner.APP,
                    enableNotification = enableNotification,
                )
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.pref_code_notification_owner_permission_settings_hint),
                )
            }
            openNotificationSettings()
        }
    }

    fun requestNotificationPermissionIfNeeded(enableNotification: Boolean): Boolean {
        val permissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationUtils.hasPostNotificationsPermission(context)
        if (!permissionRequired) {
            return false
        }
        pendingNotificationOwnerPermissionSelection = CodeNotificationOwner.APP
        pendingNotificationPermissionEnable = enableNotification
        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return true
    }

    val accessibilitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        autoInputAccessibilityEnabled =
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
        autoInputAccessibilityListed =
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceListed(context)
    }

    fun openAccessibilitySettings() {
        val accessibilityIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val appDetailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        val targetIntent = if (
            supportsAccessibilityAutoInput &&
            isAutoInputAccessibilityServiceDeclared(context) &&
            !isAutoInputAccessibilityServiceListed(context)
        ) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.pref_auto_input_accessibility_service_restricted_hint),
                )
            }
            appDetailsIntent
        } else {
            accessibilityIntent
        }
        if (activityOwner != null) {
            runCatching {
                accessibilitySettingsLauncher.launch(targetIntent)
            }.onFailure {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.pref_auto_input_accessibility_service_open_failed),
                    )
                }
            }
            return
        }
        runCatching {
            context.startActivity(targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.pref_auto_input_accessibility_service_open_failed),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        settings = repository.getVerificationSettings()
        recordSettings = repository.getRecordSettings()
        autoInputAccessibilityEnabled =
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
        autoInputAccessibilityListed =
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceListed(context)
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            autoInputAccessibilityEnabled =
                supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
            autoInputAccessibilityListed =
                supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceListed(context)
            if (
                pendingNotificationOwnerPermissionSelection == CodeNotificationOwner.APP &&
                NotificationUtils.hasPostNotificationsPermission(context)
            ) {
                val enableNotification = pendingNotificationPermissionEnable
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                persistNotificationOwnerSelection(
                    owner = CodeNotificationOwner.APP,
                    enableNotification = enableNotification,
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.pref_verification_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
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
        val current = settings ?: return@Scaffold
        val currentRecordSettings = recordSettings ?: return@Scaffold
        val historyEntries = stringArrayResource(id = R.array.history_limit_entry_list)
        val historyValues = stringArrayResource(id = R.array.history_limit_value_list)
        val retentionEntries = stringArrayResource(id = R.array.notification_retention_time_entry_list)
        val retentionValues = stringArrayResource(id = R.array.notification_retention_time_list)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
        ) {
            Spacer(modifier = Modifier.height(Const.PADDING_SMALL.dp))
            StateSwitchItem(
                title = stringResource(id = R.string.pref_verification_features_title),
                summary = stringResource(id = R.string.pref_verification_features_summary),
                checked = current.verificationFeaturesEnabled,
                modifier = Modifier.padding(horizontal = Const.PADDING_SMALL.dp),
            ) { enabled ->
                scope.launch {
                    settings = repository.updateVerificationSettings(
                        VerificationSettingsUpdate(verificationFeaturesEnabled = enabled),
                    )
                    notifySaved()
                }
            }
            SectionCard(
                title = stringResource(id = R.string.settings_group_relay),
                accordionMode = accordionMode.value,
                sectionExpanded = if (accordionMode.value) expandRelaySection else true,
                onExpandedChange = { expandRelaySection = !expandRelaySection },
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_copy_to_clipboard_title),
                    summary = stringResource(id = R.string.pref_copy_to_clipboard_summary),
                    checked = current.copyToClipboard,
                    enabled = current.verificationFeaturesEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateVerificationSettings(VerificationSettingsUpdate(copyToClipboard = enabled))
                        notifySaved()
                    }
                }
                Item(
                    title = stringResource(id = R.string.pref_relay_keywords_title),
                    summary = stringResource(id = R.string.pref_relay_keywords_summary),
                ) { showKeywordsDialog = true }
                Item(
                    title = stringResource(id = R.string.pref_relay_test_title),
                    summary = stringResource(id = R.string.pref_relay_test_summary),
                ) { showSmsTestDialog = true }
                Item(
                    title = stringResource(id = R.string.pref_code_rules_title),
                    summary = stringResource(id = R.string.pref_code_rules_summary),
                ) { onOpenRules() }
                Item(
                    title = stringResource(
                        id = R.string.pref_history_limit_title_with_target,
                        stringResource(id = R.string.record_settings_target_code),
                    ),
                    summary = stringResource(
                        id = R.string.pref_history_limit_summary,
                        historyLimitEntryLabel(
                            currentRecordSettings.codeHistoryLimit,
                            historyValues,
                            historyEntries,
                        ),
                    ),
                ) { onOpenRecords() }
            }
            SectionCard(
                title = stringResource(id = R.string.settings_group_auto_input),
                accordionMode = accordionMode.value,
                sectionExpanded = if (accordionMode.value) expandAutoInputSection else true,
                onExpandedChange = { expandAutoInputSection = !expandAutoInputSection },
            ) {
                if (supportsAccessibilityAutoInput) {
                    StateSwitchItem(
                        title = stringResource(id = R.string.pref_auto_input_accessibility_service_title),
                        summary = stringResource(id = R.string.pref_auto_input_accessibility_service_summary),
                        checked = autoInputAccessibilityEnabled,
                        onTitleClick = ::openAccessibilitySettings,
                    ) {
                        openAccessibilitySettings()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_enable_auto_input_code_title),
                    summary = stringResource(id = R.string.pref_enable_auto_input_code_summary),
                    checked = current.autoInputEnabled,
                    enabled = current.verificationFeaturesEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateVerificationSettings(VerificationSettingsUpdate(autoInputEnabled = enabled))
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_enable_auto_enter_code_title),
                    summary = stringResource(id = R.string.pref_enable_auto_enter_code_summary),
                    checked = current.autoEnterEnabled,
                    enabled = current.verificationFeaturesEnabled && current.autoInputEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateVerificationSettings(VerificationSettingsUpdate(autoEnterEnabled = enabled))
                        notifySaved()
                    }
                }
                Item(
                    title = stringResource(id = R.string.pref_auto_input_code_delay_title),
                    summary = stringResource(id = R.string.pref_auto_input_code_delay_summary, current.autoInputDelay),
                ) { showDelayDialog = true }
                Item(
                    title = stringResource(id = R.string.pref_auto_input_code_interval_title),
                    summary = stringResource(id = R.string.pref_auto_input_code_interval_summary, current.autoInputInterval),
                ) { showIntervalDialog = true }
            }
            SectionCard(
                title = stringResource(id = R.string.settings_group_notification),
                accordionMode = accordionMode.value,
                sectionExpanded = if (accordionMode.value) expandNotificationSection else true,
                onExpandedChange = { expandNotificationSection = !expandNotificationSection },
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_show_toast_title),
                    summary = stringResource(id = R.string.pref_show_toast_summary),
                    checked = current.showToast,
                    enabled = current.verificationFeaturesEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateVerificationSettings(VerificationSettingsUpdate(showToast = enabled))
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_show_code_notification_title),
                    summary = stringResource(id = R.string.pref_show_code_notification_summary),
                    checked = current.showCodeNotification,
                    enabled = current.verificationFeaturesEnabled,
                ) { enabled ->
                    if (!enabled) {
                        scope.launch {
                            settings = repository.updateVerificationSettings(
                                VerificationSettingsUpdate(showCodeNotification = false),
                            )
                            notifySaved()
                        }
                        return@StateSwitchItem
                    }
                    pendingEnableNotification = true
                    showNotificationOwnerDialog = true
                }
                Item(
                    title = stringResource(id = R.string.pref_code_notification_owner_title),
                    summary = codeNotificationOwnerItemSummary(current.notificationOwner),
                    enabled = current.verificationFeaturesEnabled,
                ) {
                    pendingEnableNotification = false
                    showNotificationOwnerDialog = true
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_auto_cancel_notification_title),
                    summary = stringResource(id = R.string.pref_auto_cancel_notification_summary),
                    checked = current.autoCancelNotification,
                    enabled = current.verificationFeaturesEnabled && current.showCodeNotification,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateVerificationSettings(
                            VerificationSettingsUpdate(autoCancelNotification = enabled),
                        )
                        notifySaved()
                    }
                }
                Item(
                    title = stringResource(id = R.string.pref_notification_retention_time_title),
                    summary = notificationRetentionEntryLabel(
                        current.notificationRetentionTime,
                        retentionValues,
                        retentionEntries,
                    ),
                    enabled = current.verificationFeaturesEnabled && current.showCodeNotification,
                ) { showRetentionDialog = true }
            }
            SectionCard(
                title = stringResource(id = R.string.settings_group_experimental),
                accordionMode = accordionMode.value,
                sectionExpanded = if (accordionMode.value) expandExperimentalSection else true,
                onExpandedChange = { expandExperimentalSection = !expandExperimentalSection },
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_block_sms_title),
                    summary = stringResource(id = R.string.pref_block_sms_summary),
                    checked = current.blockSmsEnabled,
                    enabled = current.verificationFeaturesEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateVerificationSettings(VerificationSettingsUpdate(blockSmsEnabled = enabled))
                        notifySaved()
                    }
                }
            }
            Spacer(modifier = Modifier.height(Const.PADDING_SMALL.dp))
        }
    }

    val current = settings
    if (showDelayDialog && current != null) {
        val nonNegativeNumberError = stringResource(id = R.string.pref_number_non_negative_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_auto_input_code_delay_title),
            initialValue = normalizeNumericInput(current.autoInputDelay),
            onDismiss = { showDelayDialog = false },
            validator = {
                if (parseNonNegativeLong(it) != null) null else nonNegativeNumberError
            },
        ) { updated ->
            showDelayDialog = false
            scope.launch {
                settings = repository.updateVerificationSettings(
                    VerificationSettingsUpdate(
                        autoInputDelay = normalizeNumericInput(updated),
                    ),
                )
                notifySaved()
            }
        }
    }
    if (showIntervalDialog && current != null) {
        val nonNegativeNumberError = stringResource(id = R.string.pref_number_non_negative_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_auto_input_code_interval_title),
            initialValue = normalizeNumericInput(current.autoInputInterval),
            onDismiss = { showIntervalDialog = false },
            validator = {
                if (parseNonNegativeLong(it) != null) null else nonNegativeNumberError
            },
        ) { updated ->
            showIntervalDialog = false
            scope.launch {
                settings = repository.updateVerificationSettings(
                    VerificationSettingsUpdate(
                        autoInputInterval = normalizeNumericInput(updated),
                    ),
                )
                notifySaved()
            }
        }
    }
    if (showRetentionDialog && current != null) {
        val entries = stringArrayResource(id = R.array.notification_retention_time_entry_list)
        val values = stringArrayResource(id = R.array.notification_retention_time_list)
        SingleChoiceDialog(
            title = stringResource(id = R.string.pref_notification_retention_time_title),
            options = entries.toList(),
            selectedIndex = values.indexOf(current.notificationRetentionTime).coerceAtLeast(0),
            onDismiss = { showRetentionDialog = false },
        ) { index ->
            showRetentionDialog = false
            scope.launch {
                settings = repository.updateVerificationSettings(
                    VerificationSettingsUpdate(notificationRetentionTime = values[index]),
                )
                notifySaved()
            }
        }
    }
    if (showKeywordsDialog && current != null) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_relay_keywords_title),
            initialValue = current.relayKeywords,
            onDismiss = { showKeywordsDialog = false },
            supportingText = stringResource(id = R.string.pref_relay_keywords_summary),
            singleLine = false,
            maxLines = 8,
            resetValue = PrefConst.RELAY_KEYWORDS_DEFAULT,
        ) { updated ->
            showKeywordsDialog = false
            scope.launch {
                settings = repository.updateVerificationSettings(VerificationSettingsUpdate(relayKeywords = updated))
                notifySaved()
            }
        }
    }
    if (showSmsTestDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_relay_test_title),
            initialValue = smsTestInput,
            onDismiss = { showSmsTestDialog = false },
            singleLine = false,
            maxLines = 6,
        ) { updated ->
            smsTestInput = updated
            settingsViewModel.performSmsCodeTest(updated)
            showSmsTestDialog = false
        }
    }
    if (showNotificationOwnerDialog && current != null) {
        val options = listOf(
            stringResource(id = R.string.pref_code_notification_owner_app_option),
            stringResource(id = R.string.pref_code_notification_owner_phone_option),
        )
        val selectedIndex = when (current.notificationOwner) {
            CodeNotificationOwner.PHONE -> 1
            CodeNotificationOwner.APP -> 0
            else -> 0
        }
        SingleChoiceDialog(
            title = stringResource(id = R.string.pref_code_notification_owner_title),
            options = options,
            selectedIndex = selectedIndex,
            onDismiss = {
                showNotificationOwnerDialog = false
                pendingEnableNotification = false
            },
        ) { index ->
            val owner = if (index == 1) {
                CodeNotificationOwner.PHONE
            } else {
                CodeNotificationOwner.APP
            }
            val enableNotification = pendingEnableNotification
            showNotificationOwnerDialog = false
            pendingEnableNotification = false
            if (owner == CodeNotificationOwner.APP &&
                requestNotificationPermissionIfNeeded(enableNotification)
            ) {
                return@SingleChoiceDialog
            }
            scope.launch {
                persistNotificationOwnerSelection(owner, enableNotification)
            }
        }
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

private fun historyLimitEntryLabel(
    value: String,
    values: Array<String>,
    entries: Array<String>,
): String {
    val index = values.indexOf(value)
    if (index >= 0) {
        return entries[index]
    }
    return value.takeIf { it.isNotBlank() } ?: "0"
}

@Composable
private fun codeNotificationOwnerItemSummary(owner: String): String {
    val ownerLabel = when (owner) {
        CodeNotificationOwner.PHONE -> stringResource(id = R.string.pref_code_notification_owner_phone)
        CodeNotificationOwner.APP -> stringResource(id = R.string.pref_code_notification_owner_app)
        else -> stringResource(id = R.string.pref_code_notification_owner_unselected)
    }
    return stringResource(id = R.string.pref_code_notification_owner_summary, ownerLabel)
}

private fun notificationRetentionEntryLabel(
    value: String,
    values: Array<String>,
    entries: Array<String>,
): String {
    val index = values.indexOf(value)
    if (index >= 0) {
        return entries[index]
    }
    return value.takeIf { it.isNotBlank() } ?: "0"
}

private const val AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME =
    "io.github.magisk317.relay.service.AutoInputAccessibilityService"

private fun isAutoInputAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val expectedService = ComponentName(
        context.packageName,
        AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME,
    ).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    if (enabledServices.isBlank()) return false
    return enabledServices.split(':').any { candidate ->
        candidate.equals(expectedService, ignoreCase = true)
    }
}

private fun isAutoInputAccessibilityServiceDeclared(context: android.content.Context): Boolean {
    val componentName = ComponentName(
        context.packageName,
        AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME,
    )
    val packageManager = context.packageManager
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getServiceInfo(
                componentName,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getServiceInfo(componentName, PackageManager.GET_META_DATA)
        }
    }.isSuccess
}

private fun isAutoInputAccessibilityServiceListed(context: android.content.Context): Boolean {
    val expectedComponent = ComponentName(
        context.packageName,
        AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME,
    )
    val accessibilityManager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    return accessibilityManager.getInstalledAccessibilityServiceList().any { serviceInfo ->
        val resolvedServiceInfo = serviceInfo.resolveInfo?.serviceInfo ?: return@any false
        resolvedServiceInfo.packageName == expectedComponent.packageName &&
            resolvedServiceInfo.name == expectedComponent.className
    }
}

private fun normalizeNumericInput(raw: String): String {
    val normalized = StringBuilder(raw.length)
    raw.forEach { ch ->
        when {
            ch.isWhitespace() || Character.getType(ch) == Character.FORMAT.toInt() -> Unit
            ch.digitToIntOrNull() != null -> normalized.append(ch.digitToInt())
            ch in setOf('-', '－', '﹣', '—', '–') && normalized.isEmpty() -> normalized.append('-')
            else -> normalized.append(ch)
        }
    }
    return normalized.toString()
}

private fun parseNonNegativeLong(raw: String): Long? {
    return normalizeNumericInput(raw)
        .toLongOrNull()
        ?.takeIf { it >= 0L }
}

private fun parseIntAtLeast(raw: String, min: Int): Int? {
    return normalizeNumericInput(raw)
        .toIntOrNull()
        ?.takeIf { it >= min }
}

private fun parseIntInRange(raw: String, range: IntRange): Int? {
    return normalizeNumericInput(raw)
        .toIntOrNull()
        ?.takeIf { it in range }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayConfigScreen(
    onBack: () -> Unit,
    onOpenSenders: () -> Unit,
    onOpenAppRouting: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val repository: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    var relay by remember { mutableStateOf<RelaySettingsSnapshot?>(null) }

    LaunchedEffect(Unit) {
        relay = repository.getRelaySettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.pref_relay_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        val current = relay ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
        ) {
            Spacer(modifier = Modifier.height(Const.PADDING_SMALL.dp))
            StateSwitchItem(
                title = stringResource(id = R.string.pref_relay_features_title),
                summary = stringResource(id = R.string.pref_relay_features_summary),
                checked = current.relayFeaturesEnabled,
                modifier = Modifier.padding(horizontal = Const.PADDING_SMALL.dp),
            ) { enabled ->
                scope.launch {
                    relay = repository.updateRelaySettings(RelaySettingsUpdate(relayFeaturesEnabled = enabled))
                }
            }
            if (current.relayFeaturesEnabled) {
                SectionCard(
                    title = stringResource(id = R.string.pref_relay_config_title),
                    accordionMode = false,
                    sectionExpanded = true,
                    onExpandedChange = {},
                ) {
                    Item(
                        title = stringResource(id = R.string.tab_senders),
                        summary = stringResource(id = R.string.pref_enable_forward_summary),
                    ) { onOpenSenders() }
                    Item(
                        title = stringResource(id = R.string.title_notification_rules),
                        summary = stringResource(id = R.string.subtitle_notification_rules),
                    ) { onOpenAppRouting() }
                    Item(
                        title = stringResource(id = R.string.advanced_filter_title),
                        summary = stringResource(id = R.string.advanced_filter_summary),
                    ) { onOpenFilters() }
                    Item(
                        title = stringResource(id = R.string.pref_relay_records_title),
                        summary = stringResource(id = R.string.pref_relay_records_summary),
                    ) { onOpenRecords() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardKeepAliveScreen(onBack: () -> Unit) {
    val repository: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)
    val snackbarHostState = remember { SnackbarHostState() }
    val notifySaved = {
        scope.launch {
            snackbarHostState.showSnackbar(savedSnackbarText)
        }
    }
    var settings by remember { mutableStateOf<DiagnosticsSettingsSnapshot?>(null) }
    var showRootDbIntervalDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = repository.getDiagnosticsSettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_group_background_keepalive)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
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
        val current = settings ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
        ) {
            Spacer(modifier = Modifier.height(Const.PADDING_SMALL.dp))
            SectionCard(
                title = stringResource(id = R.string.settings_group_background_keepalive),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_root_db_catchup_enable_title),
                    summary = stringResource(id = R.string.pref_root_db_catchup_enable_summary),
                    checked = current.rootDbCatchupEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(rootDbCatchupEnabled = enabled),
                        )
                        notifySaved()
                    }
                }
                Item(
                    title = stringResource(id = R.string.pref_root_db_catchup_interval_title),
                    summary = stringResource(
                        id = R.string.pref_root_db_catchup_interval_summary,
                        current.rootDbCatchupIntervalMin,
                    ),
                ) { showRootDbIntervalDialog = true }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_root_db_catchup_writeback_title),
                    summary = stringResource(id = R.string.pref_root_db_catchup_writeback_summary),
                    checked = current.rootDbCatchupWriteback,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(rootDbCatchupWriteback = enabled),
                        )
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_force_stop_recovery_title),
                    summary = stringResource(id = R.string.pref_force_stop_recovery_summary),
                    checked = current.forceStopRecoveryEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(forceStopRecoveryEnabled = enabled),
                        )
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_force_stop_recovery_relaunch_once_title),
                    summary = stringResource(id = R.string.pref_force_stop_recovery_relaunch_once_summary),
                    checked = current.forceStopRecoveryRelaunchOnceEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(forceStopRecoveryRelaunchOnceEnabled = enabled),
                        )
                        notifySaved()
                    }
                }
            }
        }
    }

    val current = settings
    if (showRootDbIntervalDialog && current != null) {
        val rootDbIntervalError = stringResource(id = R.string.pref_root_db_catchup_interval_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_root_db_catchup_interval_title),
            initialValue = normalizeNumericInput(current.rootDbCatchupIntervalMin),
            onDismiss = { showRootDbIntervalDialog = false },
            supportingText = stringResource(id = R.string.pref_root_db_catchup_interval_hint),
            validator = {
                parseIntInRange(it, 1..120)?.let { null }
                    ?: rootDbIntervalError
            },
        ) { updated ->
            showRootDbIntervalDialog = false
            scope.launch {
                settings = repository.updateDiagnosticsSettings(
                    DiagnosticsSettingsUpdate(rootDbCatchupIntervalMin = normalizeNumericInput(updated)),
                )
                notifySaved()
            }
        }
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelectionChange: ((Int) -> Unit)? = null,
    onConfirm: (Int) -> Unit,
) {
    SingleChoiceOptionDialog(
        title = title,
        options = options,
        selectedIndex = selectedIndex,
        onDismiss = onDismiss,
        onSelectionChange = onSelectionChange,
        onConfirm = onConfirm,
    )
}
