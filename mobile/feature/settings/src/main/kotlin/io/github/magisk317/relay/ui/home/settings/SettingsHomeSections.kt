package io.github.magisk317.relay.ui.home.settings

import io.github.magisk317.relay.ui.common.StateSwitchItem
import io.github.magisk317.relay.ui.common.ActionSwitchItem
import io.github.magisk317.relay.ui.common.SwitchItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.relay.contract.settings.DiagnosticsSettingsSnapshot
import io.github.magisk317.relay.contract.settings.GeneralSettingsSnapshot
import io.github.magisk317.relay.contract.settings.RelaySettingsSnapshot
import io.github.magisk317.relay.contract.settings.VerificationSettingsSnapshot
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.uikit.update.R as UiKitUpdateR
import io.github.magisk317.relay.contract.constant.RelayAppConst
import io.github.magisk317.forward.contract.constant.RelayPrefConst
import io.github.magisk317.relay.mobilefeature.settings.BuildConfig
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsCallbacks
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItem
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItems
import io.github.magisk317.uikit.preference.RuntimeLogShareEntryMode
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLabels
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLayout
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsState
import io.github.magisk317.uikit.preference.AppArrowItem
import io.github.magisk317.uikit.update.AppUpdateController
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import io.github.magisk317.uikit.surface.AppPrimaryButton
import org.koin.compose.koinInject

private val allowsLogExport: Boolean
    get() = MagiskLogPolicy.current().allowsLogExport

@Composable
internal fun SettingsGeneralSection(
    general: GeneralSettingsSnapshot,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onModuleEnabledChange: (Boolean) -> Unit,
    onAccordionModeChange: (Boolean) -> Unit,
    launcherIconVisible: Boolean,
    onLauncherIconVisibleChange: (Boolean) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onOpenThemeSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        io.github.magisk317.uikit.preference.GeneralSettingsSection(
            title = stringResource(id = R.string.settings_group_general),
            summary = stringResource(id = R.string.settings_group_general_summary),
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            accordionMode = true,
            moduleEnabled = general.moduleEnabled,
            onModuleEnabledChange = onModuleEnabledChange,
            moduleTitle = stringResource(id = UiKitR.string.pref_enable_title),
            moduleSummary = stringResource(id = UiKitR.string.pref_enable_summary),
            themeMode = null,
            onLanguageSelected = onLanguageSelected,
        ) {
            StateSwitchItem(
                title = stringResource(id = R.string.pref_settings_display_mode_title),
                summary = stringResource(id = UiKitR.string.pref_settings_display_mode_summary),
                checked = general.accordionMode,
                onCheckedChange = onAccordionModeChange,
            )
            StateSwitchItem(
                title = stringResource(id = UiKitR.string.pref_show_launcher_icon_title),
                summary = stringResource(id = UiKitR.string.pref_show_launcher_icon_summary),
                checked = launcherIconVisible,
                onCheckedChange = onLauncherIconVisibleChange,
            )
            AppArrowItem(
                title = stringResource(id = R.string.pref_theme_settings_title),
                summary = stringResource(id = R.string.pref_theme_settings_summary),
                onClick = onOpenThemeSettings,
            )
        }
    }
}

@Composable
internal fun SettingsFeaturesSection(
    verification: VerificationSettingsSnapshot,
    relay: RelaySettingsSnapshot,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onOpenVerification: () -> Unit,
    onVerificationEnabledChange: (Boolean) -> Unit,
    onOpenAdvancedRelay: () -> Unit,
    onRelayEnabledChange: (Boolean) -> Unit,
    onOpenDeviceControl: () -> Unit,
) {
    SectionCard(
        title = stringResource(id = R.string.settings_group_features),
        summary = stringResource(id = R.string.settings_group_features_summary),
        sectionExpanded = expanded,
        onExpandedChange = onExpandedChange,
        accordionMode = true,
    ) {
        ActionSwitchItem(
            title = stringResource(id = R.string.pref_verification_settings_title),
            summary = stringResource(id = R.string.pref_verification_settings_summary),
            checked = verification.verificationFeaturesEnabled,
            onClick = onOpenVerification,
            onCheckedChange = onVerificationEnabledChange,
        )
        ActionSwitchItem(
            title = stringResource(id = R.string.pref_relay_features_title),
            summary = stringResource(id = R.string.pref_relay_features_summary),
            checked = relay.relayFeaturesEnabled,
            onClick = onOpenAdvancedRelay,
            onCheckedChange = onRelayEnabledChange,
        )
        AppArrowItem(
            title = stringResource(id = R.string.device_control_entry_title),
            summary = stringResource(id = R.string.device_control_entry_summary),
            onClick = onOpenDeviceControl,
        )
    }
}

@Composable
internal fun SettingsBackupRestoreSection(
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
) {
    SectionCard(
        title = stringResource(id = R.string.pref_backup_restore_title),
        summary = stringResource(id = R.string.settings_group_backup_restore_summary),
        sectionExpanded = expanded,
        onExpandedChange = onExpandedChange,
        accordionMode = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppPrimaryButton(
                text = stringResource(id = UiKitR.string.pref_backup_title),
                onClick = onBackupClick,
                modifier = Modifier.weight(1f),
            )
            AppPrimaryButton(
                text = stringResource(id = UiKitR.string.pref_restore_title),
                onClick = onRestoreClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun SettingsDiagnosticsSection(
    diagnostics: DiagnosticsSettingsSnapshot,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onRuntimeLogTitleClick: () -> Unit,
    onRuntimeLogRetentionClick: () -> Unit,
    onClearLog: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onAnalyticsEnabledChange: (Boolean) -> Unit,
) {
    val updateController = koinInject<AppUpdateController>()
    SectionCard(
        title = stringResource(id = R.string.settings_group_others),
        summary = stringResource(id = R.string.settings_group_others_summary),
        sectionExpanded = expanded,
        onExpandedChange = onExpandedChange,
        accordionMode = true,
    ) {
        AppArrowItem(
            title = stringResource(id = R.string.diagnostics_title),
            summary = stringResource(id = R.string.diagnostics_summary),
            onClick = onOpenDiagnostics,
        )
        RuntimeLogDiagnosticsItems(
            labels = RuntimeLogDiagnosticsLabels(
                shareLogTitle = stringResource(id = UiKitR.string.pref_share_log_title),
                shareLogSummary = stringResource(id = UiKitR.string.pref_share_log_summary),
                retentionTitle = stringResource(id = UiKitR.string.pref_runtime_log_retention_days_title),
                retentionSummary = stringResource(
                    id = R.string.pref_runtime_log_retention_days_summary,
                    diagnostics.runtimeLogRetentionDays,
                ),
                clearLogTitle = stringResource(id = R.string.runtime_log_clear_confirm_title),
                clearLogSummary = stringResource(id = R.string.runtime_log_clear_summary),
            ),
            state = RuntimeLogDiagnosticsState(
                analyticsEnabled = diagnostics.analyticsEnabled,
            ),
            callbacks = RuntimeLogDiagnosticsCallbacks(
                onShareLog = onRuntimeLogTitleClick,
                onRetentionClick = onRuntimeLogRetentionClick,
                onClearLogClick = onClearLog,
                onAnalyticsEnabledChange = if (!BuildConfig.DEBUG && !BuildConfig.IS_CI_BUILD) onAnalyticsEnabledChange else null,
            ),
            layout = RuntimeLogDiagnosticsLayout(
                shareEntryMode = RuntimeLogShareEntryMode.SEPARATE_ITEM,
                itemOrder = if (allowsLogExport) {
                    listOf(
                        RuntimeLogDiagnosticsItem.SHARE_LOG,
                        RuntimeLogDiagnosticsItem.RETENTION,
                        RuntimeLogDiagnosticsItem.CLEAR_LOG,
                        RuntimeLogDiagnosticsItem.ANALYTICS,
                    )
                } else {
                    listOf(RuntimeLogDiagnosticsItem.ANALYTICS)
                },
            ),
            enabled = allowsLogExport,
        )
        AppArrowItem(
            title = stringResource(id = R.string.check_update_title),
            summary = stringResource(id = R.string.check_update_summary),
        ) {
            updateController.check(userInitiated = true)
        }
        // Unset falls back to how this build was produced; writing the switch pins the track
        // explicitly. `defaultValue` renders that fallback so the row shows the effective state.
        SwitchItem(
            title = stringResource(id = R.string.settings_track_ci_builds_title),
            summary = stringResource(id = R.string.settings_track_ci_builds_summary),
            key = RelayPrefConst.KEY_TRACK_CI_BUILDS,
            defaultValue = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD,
        )
        // A CI build checks unconditionally, so the two release switches would be rows that do
        // nothing: they only exist where the rule they feed actually applies.
        if (!BuildConfig.DEBUG && !BuildConfig.IS_CI_BUILD) {
            SwitchItem(
                title = stringResource(id = UiKitUpdateR.string.uikit_update_auto_check_on_start),
                summary = stringResource(id = R.string.settings_auto_check_on_start_summary),
                key = RelayPrefConst.KEY_AUTO_UPDATE_ON_START,
                defaultValue = true,
            )
            SwitchItem(
                title = stringResource(id = UiKitUpdateR.string.uikit_update_auto_check_wifi_only),
                summary = stringResource(id = R.string.settings_auto_check_wifi_only_summary),
                key = RelayPrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
                defaultValue = false,
            )
        }

    }
}
