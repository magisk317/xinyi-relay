package io.github.magisk317.relay.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.settings.DiagnosticsSettingsSnapshot
import io.github.magisk317.relay.contract.settings.GeneralSettingsSnapshot
import io.github.magisk317.relay.contract.settings.RelaySettingsSnapshot
import io.github.magisk317.relay.contract.settings.VerificationSettingsSnapshot
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.mobileui.BuildConfig

@Composable
internal fun SettingsGeneralSection(
    general: GeneralSettingsSnapshot,
    themeSummary: String,
    languageSummary: String,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onModuleEnabledChange: (Boolean) -> Unit,
    onAccordionModeChange: (Boolean) -> Unit,
    onThemeClick: () -> Unit,
    onLanguageClick: () -> Unit,
) {
    SectionCard(
        title = stringResource(id = R.string.settings_group_general),
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
    onRuntimeLogTitleClick: () -> Unit,
    onVerboseLogModeChange: (Boolean) -> Unit,
    onSensitiveDebugLogModeChange: (Boolean) -> Unit,
    onRuntimeLogRetentionClick: () -> Unit,
    onAutoUpdateOnStartChange: (Boolean) -> Unit,
    onAutoUpdateWifiOnlyChange: (Boolean) -> Unit,
    onAnalyticsEnabledChange: (Boolean) -> Unit,
) {
    SectionCard(
        title = stringResource(id = R.string.settings_group_others),
        sectionExpanded = expanded,
        onExpandedChange = onExpandedChange,
        accordionMode = true,
    ) {
        StateSwitchItem(
            title = stringResource(id = R.string.pref_verbose_log_mode_title),
            summary = stringResource(id = R.string.pref_verbose_log_mode_summary),
            checked = diagnostics.verboseLogMode,
            onTitleClick = onRuntimeLogTitleClick,
            onCheckedChange = onVerboseLogModeChange,
        )
        if (BuildConfig.DEBUG) {
            StateSwitchItem(
                title = stringResource(id = R.string.pref_sensitive_debug_log_mode_title),
                summary = stringResource(id = R.string.pref_sensitive_debug_log_mode_summary),
                checked = diagnostics.sensitiveDebugLogMode,
                onCheckedChange = onSensitiveDebugLogModeChange,
            )
        }
        Item(
            title = stringResource(id = R.string.pref_runtime_log_retention_days_title),
            summary = stringResource(
                id = R.string.pref_runtime_log_retention_days_summary,
                diagnostics.runtimeLogRetentionDays,
            ),
            onClick = onRuntimeLogRetentionClick,
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
        StateSwitchItem(
            title = stringResource(id = R.string.pref_enable_analytics_title),
            summary = stringResource(id = R.string.pref_enable_analytics_summary),
            checked = diagnostics.analyticsEnabled,
            onCheckedChange = onAnalyticsEnabledChange,
        )
    }
}
