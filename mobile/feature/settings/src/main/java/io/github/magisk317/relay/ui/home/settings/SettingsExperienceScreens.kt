package io.github.magisk317.relay.ui.home.settings

import android.content.Intent
import io.github.magisk317.relay.android.diagnostics.RuntimeDiagnosticsBridge
import io.github.magisk317.uikit.common.showLatestSnackbar

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
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
import io.github.magisk317.smscode.runtime.common.diagnostics.VerboseLogEnableTracker
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsHomeScreen(
    onOpenVerification: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenAdvancedRelay: () -> Unit,
    onOpenCloudBackup: (io.github.magisk317.relay.backup.BackupSource?, Boolean) -> Unit = { _, _ -> },
    onBack: (() -> Unit)? = null,
    isActive: Boolean = true,
    bottomContentPadding: Dp = 0.dp,
) {
    val workPolicy = settingsPageWorkPolicy(isActive)
    val repository: SettingsPreferencesRepository = koinInject()
    val preferenceDataSource: PreferenceDataSource = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)
    val launcherIconFailedText = stringResource(id = R.string.pref_show_launcher_icon_failed)
    val snackbarHostState = remember { SnackbarHostState() }
    val settingsViewModel = rememberSharedSettingsViewModel()
    val notifySaved: () -> Unit = {
        scope.launch {
            snackbarHostState.showLatestSnackbar(savedSnackbarText)
        }
    }
    val displayActions = rememberSettingsDisplayActions(
        settingsViewModel = settingsViewModel,
        notifySaved = notifySaved,
    )
    val backupRestoreActions = rememberSettingsBackupRestoreActions(
        settingsViewModel = settingsViewModel,
        snackbarHostState = snackbarHostState,
        isActive = isActive,
        onNavigateToCloudBackup = { source, backupNow ->
            val typedSource = when (source) {
                BackupSourceType.GOOGLE_DRIVE -> io.github.magisk317.relay.backup.BackupSource.GOOGLE_DRIVE
                BackupSourceType.WEBDAV -> io.github.magisk317.relay.backup.BackupSource.WEBDAV
                else -> null
            }
            onOpenCloudBackup(typedSource, backupNow)
        },
    )
    var general by remember { mutableStateOf<GeneralSettingsSnapshot?>(null) }
    var verification by remember { mutableStateOf<VerificationSettingsSnapshot?>(null) }
    var relay by remember { mutableStateOf<RelaySettingsSnapshot?>(null) }
    var diagnostics by remember { mutableStateOf<DiagnosticsSettingsSnapshot?>(null) }
    var launcherIconVisible by remember { mutableStateOf(true) }
    val runtimeLogActions = rememberSettingsRuntimeLogActions(
        diagnostics = diagnostics,
        repository = repository,
        snackbarHostState = snackbarHostState,
        onDiagnosticsChanged = { diagnostics = it },
        notifySaved = notifySaved,
    )
    var expandGeneral by rememberSaveable { mutableStateOf(false) }
    var expandFeatures by rememberSaveable { mutableStateOf(false) }
    var expandBackupRestore by rememberSaveable { mutableStateOf(false) }
    var expandOthers by rememberSaveable { mutableStateOf(false) }
    val navigationBarPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val effectiveBottomPadding = maxOf(bottomContentPadding, navigationBarPadding)

    LaunchedEffect(context, workPolicy) {
        if (!workPolicy.loadSnapshots) return@LaunchedEffect
        general = repository.getGeneralSettings()
        verification = repository.getVerificationSettings()
        relay = repository.getRelaySettings()
        diagnostics = repository.getDiagnosticsSettings()
        if (workPolicy.inspectLauncherIcon) {
            val visible = settingsViewModel.isLauncherIconVisible()
            launcherIconVisible = visible
            if (preferenceDataSource.getBoolean(PrefConst.KEY_SHOW_LAUNCHER_ICON, true) != visible) {
                preferenceDataSource.setBoolean(PrefConst.KEY_SHOW_LAUNCHER_ICON, visible)
                if (workPolicy.publishLauncherMirror) {
                    HookPreferenceMirror.publish(context)
                }
            }
        }
    }

    LaunchedEffect(isActive, general?.accordionMode) {
        if (!isActive) return@LaunchedEffect
        val accordionEnabled = general?.accordionMode ?: return@LaunchedEffect
        val expanded = !accordionEnabled
        expandGeneral = expanded
        expandFeatures = expanded
        expandBackupRestore = expanded
        expandOthers = expanded
    }

    val settingsHomeBody: @Composable (PaddingValues) -> Unit = { listPadding ->
    val generalSnapshot = general
    val verificationSnapshot = verification
    val relaySnapshot = relay
    val diagnosticsSnapshot = diagnostics
    if (generalSnapshot != null && verificationSnapshot != null && relaySnapshot != null && diagnosticsSnapshot != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(listPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
        ) {
            Spacer(modifier = Modifier.height(Const.SPACING_SMALL.dp))

            // Device entitlement entry (top-level)
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Const.PADDING_SMALL.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                io.github.magisk317.relay.ui.common.Item(
                    title = stringResource(id = R.string.mobile_entitlement_settings_title),
                    summary = stringResource(id = R.string.mobile_entitlement_settings_summary),
                ) {
                    context.startActivity(
                        Intent().setClassName(
                            context,
                            "io.github.magisk317.relay.entitlement.MobileEntitlementActivity",
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }

            SettingsGeneralSection(
                general = generalSnapshot,
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
                launcherIconVisible = launcherIconVisible,
                onLauncherIconVisibleChange = { visible ->
                    launcherIconVisible = visible
                    scope.launch {
                        if (settingsViewModel.setLauncherIconVisible(visible)) {
                            preferenceDataSource.setBoolean(PrefConst.KEY_SHOW_LAUNCHER_ICON, visible)
                            HookPreferenceMirror.publish(context)
                            notifySaved()
                        } else {
                            launcherIconVisible = !visible
                            preferenceDataSource.setBoolean(PrefConst.KEY_SHOW_LAUNCHER_ICON, !visible)
                            HookPreferenceMirror.publish(context)
                            snackbarHostState.showLatestSnackbar(launcherIconFailedText)
                        }
                    }
                },
                onLanguageSelected = displayActions.onLanguageSelected,
                onOpenThemeSettings = onOpenThemeSettings,
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
                    VerboseLogEnableTracker.onVerboseLogToggled(enabled)
                    SensitiveLogPolicy.setEnabled(enabled)
                    diagnostics = diagnostics?.copy(verboseLogMode = enabled)
                    scope.launch {
                        diagnostics = repository.updateDiagnosticsSettings(
                            DiagnosticsSettingsUpdate(verboseLogMode = enabled),
                        )
                        RuntimeDiagnosticsBridge.ensureInstalled()
                        RuntimeLogStore.setEnabled(enabled)
                        XLog.setLogLevel(if (enabled) Log.VERBOSE else io.github.magisk317.relay.android.BuildConfig.LOG_LEVEL)
                        notifySaved()
                    }
                },

                onRuntimeLogRetentionClick = runtimeLogActions.onRuntimeLogRetentionClick,
                onClearLog = runtimeLogActions.onClearLog,
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
            Spacer(modifier = Modifier.height(Const.PADDING_SMALL.dp + effectiveBottomPadding))
        }

    }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentUiKitStyle()) {
            UiKitStyle.Miuix -> SettingsHomeScreenMiuix(
                title = stringResource(id = R.string.tab_settings),
                onBack = onBack,
                body = settingsHomeBody,
            )

            UiKitStyle.Expressive -> SettingsHomeScreenMaterial(
                title = stringResource(id = R.string.tab_settings),
                onBack = onBack,
                body = settingsHomeBody,
            )
        }

        io.github.magisk317.uikit.common.DismissibleSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = effectiveBottomPadding),
        )
    }
}
