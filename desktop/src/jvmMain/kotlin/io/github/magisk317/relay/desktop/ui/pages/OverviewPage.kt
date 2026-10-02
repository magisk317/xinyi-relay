package io.github.magisk317.relay.desktop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.remote.SystemInfoResponse
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.remote.shouldRefreshSummaryOn
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
import io.github.magisk317.relay.desktop.session.DesktopSessionState
import io.github.magisk317.relay.desktop.ui.ActionButton
import io.github.magisk317.relay.desktop.ui.ActionTone
import io.github.magisk317.relay.desktop.ui.EmptyCard
import io.github.magisk317.relay.desktop.ui.ErrorBanner
import io.github.magisk317.relay.desktop.ui.LiveBadge
import io.github.magisk317.relay.desktop.ui.LoadingCard
import io.github.magisk317.relay.desktop.ui.MetricCard
import io.github.magisk317.relay.desktop.ui.MetricRow
import io.github.magisk317.relay.desktop.ui.MetricTone
import io.github.magisk317.relay.desktop.ui.PageShell
import io.github.magisk317.relay.desktop.ui.SurfaceCard
import io.github.magisk317.relay.desktop.ui.ConsoleMuted
import io.github.magisk317.relay.desktop.ui.formatTimestamp
import kotlinx.coroutines.launch

/**
 * Desktop port of the webUI overview page: backend status metrics and the
 * endpoint addresses, reloaded whenever a realtime device event arrives.
 */
@Composable
fun OverviewPage(
    session: DesktopSessionState,
    console: DesktopConsoleState,
    feed: DesktopRealtimeFeed,
    locale: DesktopLocale,
) {
    val scope = rememberCoroutineScope()
    var systemInfo by remember { mutableStateOf<SystemInfoResponse?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        val client = session.currentClient() ?: return
        try {
            loading = true
            error = ""
            systemInfo = client.systemInfo()
            console.refreshDevices()
        } catch (failure: Exception) {
            error = failure.message ?: DesktopMessages.t(locale, "common.loadFailed")
        } finally {
            loading = false
        }
    }

    fun reload() {
        scope.launch { load() }
    }

    LaunchedEffect(Unit) { load() }
    LaunchedEffect(feed.lastEvent) { if (shouldRefreshSummaryOn(feed.lastEvent?.type)) reload() }

    val actions: @Composable () -> Unit = {
        LiveBadge(connected = feed.connected, locale = locale)
        ActionButton(
            text = DesktopMessages.t(locale, "overview.refresh"),
            onClick = { reload() },
            tone = ActionTone.NEUTRAL,
        )
    }

    if (loading && systemInfo == null && error.isBlank()) {
        PageShell(
            title = DesktopMessages.t(locale, "overview.title"),
            description = DesktopMessages.t(locale, "overview.description"),
            badge = DesktopMessages.t(locale, "overview.title"),
            locale = locale,
            actions = actions,
        ) {
            LoadingCard(
                title = DesktopMessages.t(locale, "overview.loadingTitle"),
                message = DesktopMessages.t(locale, "overview.loadingMessage"),
                locale = locale,
            )
        }
        return
    }

    PageShell(
        title = DesktopMessages.t(locale, "overview.title"),
        description = DesktopMessages.t(locale, "overview.remoteDescription"),
        badge = DesktopMessages.t(locale, "overview.title"),
        locale = locale,
        actions = actions,
    ) {
        ErrorBanner(message = error, locale = locale)
        val info = systemInfo
        if (info != null) {
            MetricRow(
                cards = listOf(
                    {
                        MetricCard(
                            title = DesktopMessages.t(locale, "overview.metric.service"),
                            value = info.service,
                            tone = MetricTone.INFO,
                            helper = info.appEnv,
                        )
                    },
                    {
                        MetricCard(
                            title = DesktopMessages.t(locale, "overview.metric.users"),
                            value = info.userCount.toString(),
                            helper = DesktopMessages.t(locale, "overview.metric.usersHelper"),
                        )
                    },
                    {
                        MetricCard(
                            title = DesktopMessages.t(locale, "overview.metric.devicesRemote"),
                            value = console.devices.size.toString(),
                            tone = MetricTone.SUCCESS,
                            helper = DesktopMessages.t(locale, "overview.metric.devicesRemoteHelper"),
                        )
                    },
                    {
                        MetricCard(
                            title = DesktopMessages.t(locale, "overview.metric.database"),
                            value = DesktopMessages.t(
                                locale,
                                if (info.databaseReady) "common.ready" else "common.offline",
                            ),
                            tone = if (info.databaseReady) MetricTone.SUCCESS else MetricTone.WARNING,
                            helper = formatTimestamp(info.time),
                        )
                    },
                ),
            )
            SurfaceCard(
                title = DesktopMessages.t(locale, "overview.endpointsTitle"),
                subtitle = DesktopMessages.t(locale, "overview.endpointsSubtitle"),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DetailLine(
                        label = DesktopMessages.t(locale, "overview.endpoint.local"),
                        value = info.localBaseUrl.ifBlank { "-" },
                    )
                    DetailLine(
                        label = DesktopMessages.t(locale, "overview.endpoint.public"),
                        value = info.publicBaseUrl.ifBlank {
                            DesktopMessages.t(locale, "overview.endpoint.publicEmpty")
                        },
                    )
                }
            }
        } else if (error.isBlank()) {
            EmptyCard(
                title = DesktopMessages.t(locale, "overview.loadingTitle"),
                message = DesktopMessages.t(locale, "overview.loadingMessage"),
            )
        }
    }
}

/** Label + value row used by the detail cards; the label keeps its medium weight like the webUI. */
@Composable
fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = ConsoleMuted,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
