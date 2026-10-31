@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.record

import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.common.showLatestSnackbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.settings.RecordSettingsUpdate
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.relay.ui.analytics.AnalyticsChartPanel
import io.github.magisk317.relay.ui.analytics.AnalyticsChartType
import io.github.magisk317.relay.ui.analytics.AnalyticsChartWindow
import io.github.magisk317.relay.ui.common.RetentionDialog
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.uikit.preference.AppArrowItem
import io.github.magisk317.uikit.preference.SingleChoiceConfirmDialog
import io.github.magisk317.uikit.preference.StateSwitchItem
import io.github.magisk317.uikit.preference.TextInputDialog
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppBottomSheet
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Persist-target for a history-limit change on the given tab. */
internal fun recordLimitUpdate(tab: Int, value: String): RecordSettingsUpdate = when (tab) {
    0 -> RecordSettingsUpdate(codeHistoryLimit = value)
    1 -> RecordSettingsUpdate(plainSmsHistoryLimit = value)
    2 -> RecordSettingsUpdate(appNotifyHistoryLimit = value)
    else -> RecordSettingsUpdate(callNotifyHistoryLimit = value)
}

/** Persist-target for a record-toggle change on the given tab. */
internal fun recordEnabledUpdate(tab: Int, enabled: Boolean): RecordSettingsUpdate = when (tab) {
    0 -> RecordSettingsUpdate(codeRecordEnabled = enabled)
    1 -> RecordSettingsUpdate(plainSmsRecordEnabled = enabled)
    2 -> RecordSettingsUpdate(appNotifyRecordEnabled = enabled)
    else -> RecordSettingsUpdate(callNotifyRecordEnabled = enabled)
}

@Composable
internal fun RecordSettingsSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    selectedRecordTab: Int,
    currentRecordEnabled: Boolean,
    currentTabName: String,
    currentHistoryLimit: String,
    hasRecordsForTab: Boolean,
    savedSnackbarText: String,
    settingsRepository: SettingsPreferencesRepository,
    scope: CoroutineScope,
    snackbarHostState: AppSnackbarHostState,
    onRecordEnabledChange: (Boolean) -> Unit,
    onShowHistoryLimitDialog: () -> Unit,
    onShowClearDialog: () -> Unit,
    onShowExportDialog: () -> Unit,
    onShowChart: () -> Unit,
) {
    AppBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StateSwitchItem(
                title = stringResource(id = recordEnableTitleRes(selectedRecordTab)),
                summary = "",
                checked = currentRecordEnabled,
            ) { enabled ->
                onRecordEnabledChange(enabled)
                scope.launch {
                    settingsRepository.updateRecordSettings(recordEnabledUpdate(selectedRecordTab, enabled))
                    snackbarHostState.showLatestSnackbar(savedSnackbarText)
                }
            }

            AppArrowItem(
                title = stringResource(
                    id = UiKitR.string.pref_history_limit_title_with_target,
                    currentTabName,
                ),
                summary = run {
                    val entries = stringArrayResource(id = R.array.history_limit_entry_list)
                    val values = stringArrayResource(id = R.array.history_limit_value_list)
                    val index = values.indexOf(currentHistoryLimit)
                    if (index >= 0) {
                        entries[index]
                    } else {
                        "$currentHistoryLimit ${stringResource(recordTabNameRes(selectedRecordTab))}"
                    }
                },
            ) { onShowHistoryLimitDialog() }

            AppArrowItem(
                title = stringResource(id = R.string.record_settings_clear_current_tab),
                summary = currentTabName,
                enabled = hasRecordsForTab,
            ) { onShowClearDialog() }

            AppArrowItem(
                title = stringResource(id = R.string.record_export_dialog_title),
                summary = "",
            ) { onShowExportDialog() }

            AppArrowItem(
                title = stringResource(id = R.string.record_chart_entry_title),
                summary = "",
            ) { onShowChart() }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun RecordChartSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    chartState: RecordChartState,
    analyticsAllowed: Boolean,
    onChartTypeChange: (AnalyticsChartType) -> Unit,
    onChartWindowChange: (AnalyticsChartWindow) -> Unit,
) {
    AppBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(
                text = stringResource(id = R.string.record_chart_entry_title),
                role = AppTextRole.Subtitle,
            )
            AnalyticsChartPanel(
                chartType = chartState.chartType,
                chartWindow = chartState.chartWindow,
                data = chartState.snapshot,
                onChartTypeChange = onChartTypeChange,
                onChartWindowChange = onChartWindowChange,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun RecordHistoryLimitDialog(
    currentHistoryLimit: String,
    onDismiss: () -> Unit,
    onCustomLimitRequested: () -> Unit,
    onLimitSelected: (String) -> Unit,
) {
    RetentionDialog(
        selectedValue = currentHistoryLimit,
        onDismiss = onDismiss,
        titleId = UiKitR.string.pref_history_limit_title,
        entriesId = R.array.history_limit_entry_list,
        valuesId = R.array.history_limit_value_list,
    ) { value ->
        if (value == "-1") {
            onCustomLimitRequested()
        } else {
            onLimitSelected(value)
        }
        onDismiss()
    }
}

@Composable
internal fun RecordHistoryLimitInputDialog(
    currentHistoryLimit: String,
    onDismiss: () -> Unit,
    onLimitSubmitted: (String) -> Unit,
) {
    TextInputDialog(
        title = stringResource(id = R.string.history_limit_custom_entry),
        initialValue = if (currentHistoryLimit == "0" || currentHistoryLimit == "-1") {
            ""
        } else {
            currentHistoryLimit
        },
        selectAllOnOpen = true,
        onDismiss = onDismiss,
    ) { value ->
        onLimitSubmitted(value)
        onDismiss()
    }
}

@Composable
internal fun RecordExportDialog(
    selectedRecordTab: Int,
    exportScope: RecordExportScope,
    onDismiss: () -> Unit,
    onExportConfirmed: (scope: RecordExportScope, currentTab: Int, filename: String) -> Unit,
) {
    val currentTabName = stringResource(recordTabNameRes(selectedRecordTab))
    var selectedExportIndex by remember(exportScope) {
        mutableIntStateOf(if (exportScope == RecordExportScope.CURRENT_TAB) 0 else 1)
    }
    SingleChoiceConfirmDialog(
        title = stringResource(R.string.record_export_dialog_title),
        options = listOf(
            stringResource(R.string.record_export_current_tab_option, currentTabName),
            stringResource(R.string.record_export_all_tabs_option),
        ),
        selectedIndex = selectedExportIndex,
        onSelectionChange = { selectedExportIndex = it },
        onDismissRequest = onDismiss,
        onConfirm = {
            val scope = if (selectedExportIndex == 0) {
                RecordExportScope.CURRENT_TAB
            } else {
                RecordExportScope.ALL_TABS
            }
            val suffix = if (scope == RecordExportScope.ALL_TABS) {
                "all"
            } else {
                when (selectedRecordTab) {
                    0 -> "code"
                    1 -> "plain"
                    2 -> "app_notify"
                    else -> "call_notify"
                }
            }
            val filename = "Records_${suffix}_${SimpleDateFormat(
                "yyyyMMdd_HHmm",
                Locale.getDefault(),
            ).format(Date())}.json"
            onExportConfirmed(scope, selectedRecordTab, filename)
        },
    )
}

@Composable
internal fun RecordClearDialog(
    currentTabName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(stringResource(R.string.record_clear_dialog_title)) },
        text = { AppText(stringResource(R.string.record_clear_dialog_message, currentTabName)) },
        confirmButton = {
            AppTextButton(text = stringResource(R.string.action_clear_records), onClick = onConfirm)
        },
        dismissButton = {
            AppTextButton(text = stringResource(R.string.cancel), onClick = onDismiss)
        },
    )
}

@Composable
internal fun RecordTabRow(
    visible: Boolean,
    tabCounts: List<Int>,
    selectedRecordTab: Int,
    onSelectTab: (Int) -> Unit,
) {
    if (!visible) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(chromeSurfaceColor())
            .padding(horizontal = 6.dp),
    ) {
        repeat(tabCounts.size) { tabIndex ->
            val selected = selectedRecordTab == tabIndex
            val shortTitle = stringResource(recordColumnShortTitleRes(tabIndex))
            val text = if (selected) {
                "$shortTitle（${tabCounts[tabIndex]}）"
            } else {
                shortTitle
            }
            val icon = when (tabIndex) {
                0 -> Icons.Default.Lock
                1 -> Icons.Default.Email
                2 -> Icons.Default.Notifications
                else -> Icons.Default.Call
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .height(RECORD_TAB_ITEM_HEIGHT)
                    .clickable { onSelectTab(tabIndex) },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AppIcon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) {
                            appColor(AppColorRole.Primary)
                        } else {
                            appColor(AppColorRole.OnSurfaceVariant)
                        },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AppText(
                        text = text,
                        role = AppTextRole.Footnote,
                        color = if (selected) {
                            appColor(AppColorRole.Primary)
                        } else {
                            appColor(AppColorRole.OnSurfaceVariant)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
