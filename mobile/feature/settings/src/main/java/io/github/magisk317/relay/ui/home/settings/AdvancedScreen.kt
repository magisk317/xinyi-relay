package io.github.magisk317.relay.ui.home.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.surface.chromeTopAppBarColors

private const val BENCHMARK_ADVANCED_RELAY_CONFIG = "xinyi_benchmark_advanced_relay_config"

private fun Modifier.advancedBenchmarkTag(enabled: Boolean): Modifier =
    if (enabled) testTag(BENCHMARK_ADVANCED_RELAY_CONFIG) else this

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.tab_advanced)) },
                colors = chromeTopAppBarColors(),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(
                    state = rememberScrollState(),
                    enabled = workPolicy.enableScrolling,
                )
                .padding(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + effectiveBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AdvancedEntryCard(
                title = stringResource(id = R.string.pref_verification_config_title),
                subtitle = stringResource(id = R.string.pref_verification_config_summary),
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = onVerificationConfigClick,
            )
            AdvancedEntryCard(
                title = stringResource(id = R.string.pref_relay_config_title),
                subtitle = stringResource(id = R.string.pref_relay_config_summary),
                icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                modifier = Modifier.advancedBenchmarkTag(workPolicy.exposeBenchmarkTags),
                onClick = onRelayConfigClick,
            )
            AdvancedEntryCard(
                title = stringResource(id = R.string.settings_group_background_keepalive),
                subtitle = stringResource(id = R.string.advanced_keepalive_summary),
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = onForwardKeepAliveClick,
            )
            AdvancedEntryCard(
                title = stringResource(id = R.string.scheduled_reminder_entry_title),
                subtitle = stringResource(id = R.string.scheduled_reminder_entry_summary),
                icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                onClick = onScheduledReminderClick,
            )
            onNavigateToScheduledTasks?.let {
                AdvancedEntryCard(
                    title = stringResource(id = R.string.scheduled_task_entry_title),
                    subtitle = stringResource(id = R.string.scheduled_task_entry_summary),
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    onClick = it,
                )
            }
            AdvancedEntryCard(
                title = stringResource(id = R.string.pref_remote_agent_title),
                subtitle = stringResource(id = R.string.pref_remote_agent_summary),
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = onRemoteAgentClick,
            )
            AdvancedEntryCard(
                title = stringResource(id = R.string.advanced_filter_title),
                subtitle = stringResource(id = R.string.advanced_filter_summary),
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                onClick = onInterceptClick,
            )
        }
    }
}

@Composable
private fun AdvancedEntryCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth().let { base ->
            if (onClick != null) {
                base.clickable(onClick = onClick)
            } else {
                base
            }
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    trailingContent?.invoke()
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showChevron) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}
