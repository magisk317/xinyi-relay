package io.github.magisk317.relay.ui.home

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.common.constant.Const
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.diagnostics.LogBundleExporter
import io.github.magisk317.relay.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.common.utils.XLog
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
import io.github.magisk317.relay.ui.common.SingleChoiceOptionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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
    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)
    val snackbarHostState = remember { SnackbarHostState() }
    val notifySaved = {
        scope.launch {
            snackbarHostState.showSnackbar(savedSnackbarText)
        }
    }
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val themeState by settingsViewModel.themeState.collectAsStateWithLifecycle()
    val languageState by settingsViewModel.languageState.collectAsStateWithLifecycle()
    var general by remember { mutableStateOf<GeneralSettingsSnapshot?>(null) }
    var verification by remember { mutableStateOf<VerificationSettingsSnapshot?>(null) }
    var relay by remember { mutableStateOf<RelaySettingsSnapshot?>(null) }
    var diagnostics by remember { mutableStateOf<DiagnosticsSettingsSnapshot?>(null) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showRootDbIntervalDialog by remember { mutableStateOf(false) }
    var showRuntimeLogDialog by remember { mutableStateOf(false) }
    var themeDialogInitialMode by remember { mutableStateOf(0) }
    var themeDialogSelectedMode by remember { mutableStateOf(0) }
    var languageDialogInitialTag by remember { mutableStateOf("") }
    var languageDialogSelectedTag by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        general = repository.getGeneralSettings()
        verification = repository.getVerificationSettings()
        relay = repository.getRelaySettings()
        diagnostics = repository.getDiagnosticsSettings()
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
                sectionExpanded = true,
                onExpandedChange = {},
                accordionMode = false,
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
                sectionExpanded = true,
                onExpandedChange = {},
                accordionMode = false,
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
                title = stringResource(id = R.string.settings_group_others),
                sectionExpanded = true,
                onExpandedChange = {},
                accordionMode = false,
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_root_db_catchup_enable_title),
                    summary = stringResource(id = R.string.pref_root_db_catchup_enable_summary),
                    checked = diagnosticsSnapshot.rootDbCatchupEnabled,
                ) { enabled ->
                    scope.launch {
                        diagnostics = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(rootDbCatchupEnabled = enabled),
                        )
                        notifySaved()
                    }
                }
                Item(
                    title = stringResource(id = R.string.pref_root_db_catchup_interval_title),
                    summary = stringResource(
                        id = R.string.pref_root_db_catchup_interval_summary,
                        diagnosticsSnapshot.rootDbCatchupIntervalMin,
                    ),
                ) { showRootDbIntervalDialog = true }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_force_stop_recovery_title),
                    summary = stringResource(id = R.string.pref_force_stop_recovery_summary),
                    checked = diagnosticsSnapshot.forceStopRecoveryEnabled,
                ) { enabled ->
                    scope.launch {
                        diagnostics = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(forceStopRecoveryEnabled = enabled),
                        )
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_force_stop_recovery_relaunch_once_title),
                    summary = stringResource(id = R.string.pref_force_stop_recovery_relaunch_once_summary),
                    checked = diagnosticsSnapshot.forceStopRecoveryRelaunchOnceEnabled,
                ) { enabled ->
                    scope.launch {
                        diagnostics = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(forceStopRecoveryRelaunchOnceEnabled = enabled),
                        )
                        notifySaved()
                    }
                }
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
                                snackbarHostState.showSnackbar("导出失败: ${result.details}")
                                return@launch
                            }
                            runCatching {
                                LogBundleExporter.shareLogBundle(context, file)
                            }.onFailure {
                                snackbarHostState.showSnackbar("分享失败: ${it.message}")
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
    if (showRootDbIntervalDialog && currentDiagnostics != null) {
        val rootDbIntervalError = stringResource(id = R.string.pref_root_db_catchup_interval_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_root_db_catchup_interval_title),
            initialValue = currentDiagnostics.rootDbCatchupIntervalMin,
            onDismiss = { showRootDbIntervalDialog = false },
            supportingText = stringResource(id = R.string.pref_root_db_catchup_interval_hint),
            validator = {
                it.toIntOrNull()?.takeIf { value -> value in 1..120 }?.let { null }
                    ?: rootDbIntervalError
            },
        ) { updated ->
            showRootDbIntervalDialog = false
            scope.launch {
                diagnostics = repository.updateDiagnosticsSettings(
                    DiagnosticsSettingsUpdate(rootDbCatchupIntervalMin = updated),
                )
                notifySaved()
            }
        }
    }
    if (showRuntimeLogDialog && currentDiagnostics != null) {
        val runtimeLogFileSizeError = stringResource(id = R.string.pref_runtime_log_file_size_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_runtime_log_file_size_title),
            initialValue = currentDiagnostics.runtimeLogFileSizeMb.toString(),
            onDismiss = { showRuntimeLogDialog = false },
            supportingText = stringResource(id = R.string.pref_runtime_log_file_size_hint),
            validator = {
                it.toIntOrNull()?.takeIf { value -> value >= PrefConst.RUNTIME_LOG_FILE_SIZE_MB_MIN }?.let { null }
                    ?: runtimeLogFileSizeError
            },
        ) { updated ->
            showRuntimeLogDialog = false
            scope.launch {
                diagnostics = repository.updateDiagnosticsSettings(
                    DiagnosticsSettingsUpdate(runtimeLogFileSizeMb = updated.toInt()),
                )
                notifySaved()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationSettingsScreen(
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val repository: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)
    val snackbarHostState = remember { SnackbarHostState() }
    val notifySaved = {
        scope.launch {
            snackbarHostState.showSnackbar(savedSnackbarText)
        }
    }
    val settingsViewModel: SettingsViewModel = koinViewModel()
    var settings by remember { mutableStateOf<VerificationSettingsSnapshot?>(null) }
    var recordSettings by remember { mutableStateOf<RecordSettingsSnapshot?>(null) }
    var showDelayDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showKeywordsDialog by remember { mutableStateOf(false) }
    var showSmsTestDialog by remember { mutableStateOf(false) }
    var smsTestInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        settings = repository.getVerificationSettings()
        recordSettings = repository.getRecordSettings()
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
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
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
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
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
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
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
            }
            SectionCard(
                title = stringResource(id = R.string.settings_group_experimental),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
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
            initialValue = current.autoInputDelay,
            onDismiss = { showDelayDialog = false },
            validator = {
                it.toLongOrNull()?.takeIf { value -> value >= 0L }?.let { null }
                    ?: nonNegativeNumberError
            },
        ) { updated ->
            showDelayDialog = false
            scope.launch {
                settings = repository.updateVerificationSettings(VerificationSettingsUpdate(autoInputDelay = updated))
                notifySaved()
            }
        }
    }
    if (showIntervalDialog && current != null) {
        val nonNegativeNumberError = stringResource(id = R.string.pref_number_non_negative_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_auto_input_code_interval_title),
            initialValue = current.autoInputInterval,
            onDismiss = { showIntervalDialog = false },
            validator = {
                it.toLongOrNull()?.takeIf { value -> value >= 0L }?.let { null }
                    ?: nonNegativeNumberError
            },
        ) { updated ->
            showIntervalDialog = false
            scope.launch {
                settings = repository.updateVerificationSettings(VerificationSettingsUpdate(autoInputInterval = updated))
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
fun DiagnosticsSettingsScreen(onBack: () -> Unit) {
    val repository: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)
    val snackbarHostState = remember { SnackbarHostState() }
    val notifySaved = {
        scope.launch {
            snackbarHostState.showSnackbar(savedSnackbarText)
        }
    }
    var settings by remember { mutableStateOf<DiagnosticsSettingsSnapshot?>(null) }
    var showRootDbIntervalDialog by remember { mutableStateOf(false) }
    var showRuntimeLogDialog by remember { mutableStateOf(false) }
    var showRuntimeLogSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings = repository.getDiagnosticsSettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.advanced_diagnostics_title)) },
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
            SectionCard(
                title = stringResource(id = R.string.settings_group_others),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_verbose_log_mode_title),
                    summary = stringResource(id = R.string.pref_verbose_log_mode_summary),
                    checked = current.verboseLogMode,
                    onTitleClick = { showRuntimeLogSheet = true },
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(DiagnosticsSettingsUpdate(verboseLogMode = enabled))
                        notifySaved()
                    }
                }
                Item(
                    title = stringResource(id = R.string.pref_runtime_log_file_size_title),
                    summary = stringResource(
                        id = R.string.pref_runtime_log_file_size_summary,
                        current.runtimeLogFileSizeMb,
                    ),
                ) { showRuntimeLogDialog = true }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_auto_update_on_start_title),
                    summary = stringResource(id = R.string.pref_auto_update_on_start_summary),
                    checked = current.autoUpdateOnStart,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(DiagnosticsSettingsUpdate(autoUpdateOnStart = enabled))
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_auto_update_wifi_only_title),
                    summary = stringResource(id = R.string.pref_auto_update_wifi_only_summary),
                    checked = current.autoUpdateWifiOnly,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(autoUpdateWifiOnly = enabled),
                        )
                        notifySaved()
                    }
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_enable_analytics_title),
                    summary = stringResource(id = R.string.pref_enable_analytics_summary),
                    checked = current.analyticsEnabled,
                ) { enabled ->
                    scope.launch {
                        settings = repository.updateDiagnosticsSettings(DiagnosticsSettingsUpdate(analyticsEnabled = enabled))
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
            initialValue = current.rootDbCatchupIntervalMin,
            onDismiss = { showRootDbIntervalDialog = false },
            supportingText = stringResource(id = R.string.pref_root_db_catchup_interval_hint),
            validator = {
                it.toIntOrNull()?.takeIf { value -> value in 1..120 }?.let { null }
                    ?: rootDbIntervalError
            },
        ) { updated ->
            showRootDbIntervalDialog = false
            scope.launch {
                settings = repository.updateDiagnosticsSettings(
                    DiagnosticsSettingsUpdate(rootDbCatchupIntervalMin = updated),
                )
                notifySaved()
            }
        }
    }
    if (showRuntimeLogDialog && current != null) {
        val runtimeLogFileSizeError = stringResource(id = R.string.pref_runtime_log_file_size_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_runtime_log_file_size_title),
            initialValue = current.runtimeLogFileSizeMb.toString(),
            onDismiss = { showRuntimeLogDialog = false },
            supportingText = stringResource(id = R.string.pref_runtime_log_file_size_hint),
            validator = {
                it.toIntOrNull()?.takeIf { value -> value >= PrefConst.RUNTIME_LOG_FILE_SIZE_MB_MIN }?.let { null }
                    ?: runtimeLogFileSizeError
            },
        ) { updated ->
            showRuntimeLogDialog = false
            scope.launch {
                settings = repository.updateDiagnosticsSettings(
                    DiagnosticsSettingsUpdate(runtimeLogFileSizeMb = updated.toInt()),
                )
                notifySaved()
            }
        }
    }
    if (showRuntimeLogSheet) {
        RuntimeLogViewerSheet(
            onDismiss = { showRuntimeLogSheet = false },
        )
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
