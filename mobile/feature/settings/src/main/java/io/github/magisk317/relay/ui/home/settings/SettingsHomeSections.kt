package io.github.magisk317.relay.ui.home.settings

import io.github.magisk317.relay.ui.common.StateSwitchItem
import io.github.magisk317.relay.ui.common.ActionSwitchItem
import io.github.magisk317.relay.ui.common.Item
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.ui.common.SectionCard
import io.github.magisk317.relay.contract.settings.DiagnosticsSettingsSnapshot
import io.github.magisk317.relay.contract.settings.GeneralSettingsSnapshot
import io.github.magisk317.relay.contract.settings.RelaySettingsSnapshot
import io.github.magisk317.relay.contract.settings.VerificationSettingsSnapshot
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.mobilefeature.settings.BuildConfig
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsCallbacks
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItem
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItems
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLabels
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLayout
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsState

@Composable
internal fun SettingsGeneralSection(
    general: GeneralSettingsSnapshot,
    themeSummary: String,
    languageSummary: String,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onModuleEnabledChange: (Boolean) -> Unit,
    onAccordionModeChange: (Boolean) -> Unit,
    launcherIconVisible: Boolean,
    onLauncherIconVisibleChange: (Boolean) -> Unit,
    onThemeClick: () -> Unit,
    onLanguageClick: () -> Unit,
) {
    SectionCard(
        title = stringResource(id = R.string.settings_group_general),
        summary = stringResource(id = R.string.settings_group_general_summary),
        sectionExpanded = expanded,
        onExpandedChange = onExpandedChange,
        accordionMode = true,
    ) {
        StateSwitchItem(
            title = stringResource(id = R.string.pref_enable_title),
            summary = stringResource(id = R.string.pref_enable_summary),
            checked = general.moduleEnabled,
            onCheckedChange = onModuleEnabledChange,
        )
        StateSwitchItem(
            title = stringResource(id = R.string.pref_settings_display_mode_title),
            summary = stringResource(id = R.string.pref_settings_display_mode_summary),
            checked = general.accordionMode,
            onCheckedChange = onAccordionModeChange,
        )
        StateSwitchItem(
            title = stringResource(id = R.string.pref_show_launcher_icon_title),
            summary = stringResource(id = R.string.pref_show_launcher_icon_summary),
            checked = launcherIconVisible,
            onCheckedChange = onLauncherIconVisibleChange,
        )
        Item(
            title = stringResource(id = R.string.pref_choose_theme_title),
            summary = themeSummary,
            onClick = onThemeClick,
        )
        Item(
            title = stringResource(id = R.string.pref_language_title),
            summary = languageSummary,
            onClick = onLanguageClick,
        )
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
            Button(
                onClick = onBackupClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(id = R.string.pref_backup_title))
            }
            Button(
                onClick = onRestoreClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(id = R.string.pref_restore_title))
            }
        }
    }
}

@Composable
internal fun SettingsDiagnosticsSection(
    diagnostics: DiagnosticsSettingsSnapshot,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onOpenMobileEntitlement: () -> Unit,
    onRuntimeLogTitleClick: () -> Unit,
    onVerboseLogModeChange: (Boolean) -> Unit,
    onSensitiveDebugLogModeChange: (Boolean) -> Unit,
    onRuntimeLogRetentionClick: () -> Unit,
    onClearLog: () -> Unit,
    onAutoUpdateOnStartChange: (Boolean) -> Unit,
    onAutoUpdateWifiOnlyChange: (Boolean) -> Unit,
    onAnalyticsEnabledChange: (Boolean) -> Unit,
) {
    SectionCard(
        title = stringResource(id = R.string.settings_group_others),
        summary = stringResource(id = R.string.settings_group_others_summary),
        sectionExpanded = expanded,
        onExpandedChange = onExpandedChange,
        accordionMode = true,
    ) {
        Item(
            title = stringResource(id = R.string.mobile_entitlement_settings_title),
            summary = stringResource(id = R.string.mobile_entitlement_settings_summary),
            onClick = onOpenMobileEntitlement,
        )
        RuntimeLogDiagnosticsItems(
            labels = RuntimeLogDiagnosticsLabels(
                verboseLogTitle = stringResource(id = R.string.pref_verbose_log_mode_title),
                verboseLogSummary = stringResource(id = R.string.pref_verbose_log_mode_summary),
                retentionTitle = stringResource(id = R.string.pref_runtime_log_retention_days_title),
                retentionSummary = stringResource(
                    id = R.string.pref_runtime_log_retention_days_summary,
                    diagnostics.runtimeLogRetentionDays,
                ),
                clearLogTitle = stringResource(id = R.string.runtime_log_clear_confirm_title),
                clearLogSummary = stringResource(id = R.string.runtime_log_clear_summary),
                sensitiveLogTitle = if (BuildConfig.DEBUG) {
                    stringResource(id = R.string.pref_sensitive_debug_log_mode_title)
                } else {
                    null
                },
                sensitiveLogSummary = if (BuildConfig.DEBUG) {
                    stringResource(id = R.string.pref_sensitive_debug_log_mode_summary)
                } else {
                    ""
                },
            ),
            state = RuntimeLogDiagnosticsState(
                verboseLogEnabled = diagnostics.verboseLogMode,
                sensitiveLogEnabled = diagnostics.sensitiveDebugLogMode,
            ),
            callbacks = RuntimeLogDiagnosticsCallbacks(
                onShareLog = onRuntimeLogTitleClick,
                onVerboseLogEnabledChange = onVerboseLogModeChange,
                onRetentionClick = onRuntimeLogRetentionClick,
                onClearLogClick = onClearLog,
                onSensitiveLogEnabledChange = if (BuildConfig.DEBUG) {
                    onSensitiveDebugLogModeChange
                } else {
                    null
                },
            ),
            layout = RuntimeLogDiagnosticsLayout(
                itemOrder = listOf(
                    RuntimeLogDiagnosticsItem.VERBOSE_LOG,
                    RuntimeLogDiagnosticsItem.SENSITIVE_LOG,
                    RuntimeLogDiagnosticsItem.RETENTION,
                    RuntimeLogDiagnosticsItem.CLEAR_LOG,
                ),
            ),
        )
        StateSwitchItem(
            title = stringResource(id = R.string.pref_auto_update_on_start_title),
            summary = stringResource(id = R.string.pref_auto_update_on_start_summary),
            checked = diagnostics.autoUpdateOnStart,
            onCheckedChange = onAutoUpdateOnStartChange,
        )
        if (diagnostics.autoUpdateOnStart) {
            StateSwitchItem(
                title = stringResource(id = R.string.pref_auto_update_wifi_only_title),
                summary = stringResource(id = R.string.pref_auto_update_wifi_only_summary),
                checked = diagnostics.autoUpdateWifiOnly,
                onCheckedChange = onAutoUpdateWifiOnlyChange,
            )
        }
        if (!BuildConfig.DEBUG) {
            StateSwitchItem(
                title = stringResource(id = R.string.pref_enable_analytics_title),
                summary = stringResource(id = R.string.pref_enable_analytics_summary),
                checked = diagnostics.analyticsEnabled,
                onCheckedChange = onAnalyticsEnabledChange,
            )
        }
    }
}
