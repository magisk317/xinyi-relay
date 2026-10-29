package io.github.magisk317.relay.ui.home.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.constant.RelayAppConst
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.uikit.preference.AppArrowItem
import io.github.magisk317.uikit.preference.SectionList
import io.github.magisk317.uikit.surface.DetailDivider
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle

private const val BENCHMARK_ADVANCED_RELAY_CONFIG = "xinyi_benchmark_advanced_relay_config"

private fun Modifier.advancedBenchmarkTag(enabled: Boolean): Modifier =
    if (enabled) testTag(BENCHMARK_ADVANCED_RELAY_CONFIG) else this

/**
 * The advanced tab: one flat group of entry rows, no card and no section header.
 *
 * Each row here opens its own screen, so there is nothing left for a group header to name: the
 * tab title is already in the top bar of [AdvancedScreenMaterial] / [AdvancedScreenMiuix], and any
 * summary of the group is just a hand-copied list of the rows below it - the one text on this page
 * that can go stale while every check still passes. Keep a header only if this tab ever grows a
 * second group; that is what [io.github.magisk317.uikit.preference.SectionCard] is for.
 */
@Composable
fun AdvancedScreen(
    bottomContentPadding: Dp = 0.dp,
    isActive: Boolean = true,
    benchmarkTagsEnabled: Boolean = true,
    onNavigateToScheduledTasks: (() -> Unit)? = null,
    onInterceptClick: () -> Unit,
    onVerificationConfigClick: () -> Unit,
    onRelayConfigClick: () -> Unit,
    onForwardKeepAliveClick: () -> Unit,
    onScheduledReminderClick: () -> Unit,
    onRemoteAgentClick: () -> Unit,
) {
    val workPolicy = advancedPageWorkPolicy(isActive, benchmarkTagsEnabled)
    val navigationBarPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val effectiveBottomPadding = maxOf(bottomContentPadding, navigationBarPadding)
    val advancedBody: @Composable (PaddingValues) -> Unit = { listPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(listPadding)
                .verticalScroll(
                    state = rememberScrollState(),
                    enabled = workPolicy.enableScrolling,
                ),
            verticalArrangement = Arrangement.spacedBy(RelayAppConst.SPACING_SMALL.dp),
        ) {
            Spacer(modifier = Modifier.height(RelayAppConst.SPACING_SMALL.dp))
            SectionList {
                AppArrowItem(
                    title = stringResource(id = R.string.pref_verification_config_title),
                    summary = stringResource(id = R.string.pref_verification_config_summary),
                    onClick = onVerificationConfigClick,
                )
                DetailDivider()
                AppArrowItem(
                    title = stringResource(id = R.string.pref_relay_config_title),
                    summary = stringResource(id = R.string.pref_relay_config_summary),
                    modifier = Modifier.advancedBenchmarkTag(workPolicy.exposeBenchmarkTags),
                    onClick = onRelayConfigClick,
                )
                DetailDivider()
                AppArrowItem(
                    title = stringResource(id = R.string.settings_group_background_keepalive),
                    summary = stringResource(id = R.string.advanced_keepalive_summary),
                    onClick = onForwardKeepAliveClick,
                )
                DetailDivider()
                AppArrowItem(
                    title = stringResource(id = R.string.scheduled_reminder_entry_title),
                    summary = stringResource(id = R.string.scheduled_reminder_entry_summary),
                    onClick = onScheduledReminderClick,
                )
                onNavigateToScheduledTasks?.let { navigate ->
                    DetailDivider()
                    AppArrowItem(
                        title = stringResource(id = R.string.scheduled_task_entry_title),
                        summary = stringResource(id = R.string.scheduled_task_entry_summary),
                        onClick = navigate,
                    )
                }
                DetailDivider()
                AppArrowItem(
                    title = stringResource(id = R.string.pref_remote_agent_title),
                    summary = stringResource(id = R.string.pref_remote_agent_summary),
                    onClick = onRemoteAgentClick,
                )
                DetailDivider()
                AppArrowItem(
                    title = stringResource(id = R.string.advanced_filter_title),
                    summary = stringResource(id = R.string.advanced_filter_summary),
                    onClick = onInterceptClick,
                )
            }
            Spacer(
                modifier = Modifier.height(
                    RelayAppConst.PADDING_SMALL.dp + effectiveBottomPadding,
                ),
            )
        }
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> AdvancedScreenMiuix(
            title = stringResource(id = R.string.tab_advanced),
            body = advancedBody,
        )

        UiKitStyle.Expressive -> AdvancedScreenMaterial(
            title = stringResource(id = R.string.tab_advanced),
            body = advancedBody,
        )
    }
}
