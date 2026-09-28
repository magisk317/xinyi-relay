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
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.common.Item
import io.github.magisk317.relay.ui.common.SectionCard
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle

private const val BENCHMARK_ADVANCED_RELAY_CONFIG = "xinyi_benchmark_advanced_relay_config"

private fun Modifier.advancedBenchmarkTag(enabled: Boolean): Modifier =
    if (enabled) testTag(BENCHMARK_ADVANCED_RELAY_CONFIG) else this

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
            SectionCard(
                title = stringResource(id = R.string.settings_group_advanced),
                summary = stringResource(id = R.string.settings_group_advanced_summary),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
                Item(
                    title = stringResource(id = R.string.pref_verification_config_title),
                    summary = stringResource(id = R.string.pref_verification_config_summary),
                    onClick = onVerificationConfigClick,
                )
                Item(
                    title = stringResource(id = R.string.pref_relay_config_title),
                    summary = stringResource(id = R.string.pref_relay_config_summary),
                    modifier = Modifier.advancedBenchmarkTag(workPolicy.exposeBenchmarkTags),
                    onClick = onRelayConfigClick,
                )
                Item(
                    title = stringResource(id = R.string.settings_group_background_keepalive),
                    summary = stringResource(id = R.string.advanced_keepalive_summary),
                    onClick = onForwardKeepAliveClick,
                )
                Item(
                    title = stringResource(id = R.string.scheduled_reminder_entry_title),
                    summary = stringResource(id = R.string.scheduled_reminder_entry_summary),
                    onClick = onScheduledReminderClick,
                )
                onNavigateToScheduledTasks?.let { navigate ->
                    Item(
                        title = stringResource(id = R.string.scheduled_task_entry_title),
                        summary = stringResource(id = R.string.scheduled_task_entry_summary),
                        onClick = navigate,
                    )
                }
                Item(
                    title = stringResource(id = R.string.pref_remote_agent_title),
                    summary = stringResource(id = R.string.pref_remote_agent_summary),
                    onClick = onRemoteAgentClick,
                )
                Item(
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
