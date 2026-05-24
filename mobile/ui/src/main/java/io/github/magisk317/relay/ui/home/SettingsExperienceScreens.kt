package io.github.magisk317.relay.ui.home

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore
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
import kotlinx.coroutines.launch
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
    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)
    val snackbarHostState = remember { SnackbarHostState() }
    val notifySaved: () -> Unit = {
        scope.launch {
            snackbarHostState.showSnackbar(savedSnackbarText)
        }
    }
    val settingsViewModel = rememberSharedSettingsViewModel()
    val themeState by settingsViewModel.themeState.collectAsStateWithLifecycle()
    val languageState by settingsViewModel.languageState.collectAsStateWithLifecycle()
    val displayActions = rememberSettingsDisplayActions(
        settingsViewModel = settingsViewModel,
        themeMode = themeState.mode,
        languageTag = languageState.languageTag,
        notifySaved = notifySaved,
    )
    val backupRestoreActions = rememberSettingsBackupRestoreActions(
        settingsViewModel = settingsViewModel,
        snackbarHostState = snackbarHostState,
    )
    var general by remember { mutableStateOf<GeneralSettingsSnapshot?>(null) }
    var verification by remember { mutableStateOf<VerificationSettingsSnapshot?>(null) }
    var relay by remember { mutableStateOf<RelaySettingsSnapshot?>(null) }
    var diagnostics by remember { mutableStateOf<DiagnosticsSettingsSnapshot?>(null) }
    val runtimeLogActions = rememberSettingsRuntimeLogActions(
        diagnostics = diagnostics,
        repository = repository,
        snackbarHostState = snackbarHostState,
        onDiagnosticsChanged = { diagnostics = it },
        notifySaved = notifySaved,
    )
    var expandGeneral by rememberSaveable { mutableStateOf(false) }
    var expandFeatures by rememberSaveable { mutableStateOf(false) }
    var expandSupport by rememberSaveable { mutableStateOf(false) }
    var expandBackupRestore by rememberSaveable { mutableStateOf(false) }
    var expandOthers by rememberSaveable { mutableStateOf(false) }

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
                onThemeClick = displayActions.onThemeClick,
                onLanguageClick = displayActions.onLanguageClick,
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
                onBackupClick = backupRestoreActions.onBackupClick,
                onRestoreClick = backupRestoreActions.onRestoreClick,
            )
            SettingsDiagnosticsSection(
                diagnostics = diagnosticsSnapshot,
                expanded = expandOthers,
                onExpandedChange = { expandOthers = !expandOthers },
                onRuntimeLogTitleClick = runtimeLogActions.onRuntimeLogTitleClick,
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
                onRuntimeLogRetentionClick = runtimeLogActions.onRuntimeLogRetentionClick,
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
}
