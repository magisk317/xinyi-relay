package io.github.magisk317.relay.desktop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.github.magisk317.relay.contract.remote.DeviceConfigAuditLogItem
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandResponse
import io.github.magisk317.relay.contract.remote.DeviceItem
import io.github.magisk317.relay.contract.remote.RelayRecord
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
import io.github.magisk317.relay.desktop.ui.RelayBadge
import io.github.magisk317.relay.desktop.ui.RelayTone
import io.github.magisk317.relay.desktop.ui.SurfaceCard
import io.github.magisk317.relay.desktop.ui.formatTimestamp
import kotlinx.coroutines.launch

/**
 * Desktop port of the webUI analytics page: record-type counters over the
 * last 200 records, the selected device's pending config commands and the
 * most recently seen devices.
 */
@Composable
fun AnalyticsPage(
    session: DesktopSessionState,
    console: DesktopConsoleState,
    feed: DesktopRealtimeFeed,
    locale: DesktopLocale,
) {
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<RelayRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var auditLogs by remember { mutableStateOf<List<DeviceConfigAuditLogItem>>(emptyList()) }

    suspend fun load() {
        try {
            loading = true
            error = ""
            val client = session.currentClient()
            console.refreshDevices()
            records = client?.records(limit = 200)?.records ?: emptyList()
            console.refreshConfig()
            auditLogs = try {
                val auditDeviceId = console.selectedDeviceId
                if (client != null && auditDeviceId != null) {
                    client.deviceConfigAuditLogs(auditDeviceId, AUDIT_LOG_LIMIT, 0).logs
                } else {
                    emptyList()
                }
            } catch (failure: Exception) {
                // The audit endpoint is best-effort: a failure degrades to an
                // empty trail (the Tauri page does the same) so the rest of the
                // page still renders.
                emptyList()
            }
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
            text = DesktopMessages.t(locale, "analytics.refresh"),
            onClick = { reload() },
            tone = ActionTone.NEUTRAL,
        )
    }

    if (loading && records.isEmpty() && error.isBlank()) {
        PageShell(
            title = DesktopMessages.t(locale, "analytics.title"),
            description = DesktopMessages.t(locale, "analytics.description"),
            badge = DesktopMessages.t(locale, "analytics.title"),
            locale = locale,
            actions = actions,
        ) {
            LoadingCard(
                title = DesktopMessages.t(locale, "analytics.loadingTitle"),
                message = DesktopMessages.t(locale, "analytics.loadingMessage"),
                locale = locale,
            )
        }
        return
    }

    val smsCode = records.count { it.recordType == "sms_code" }
    val smsPlain = records.count { it.recordType == "sms_plain" }
    val appNotify = records.count { it.recordType == "app_notify" }
    val callNotify = records.count { it.recordType == "call" }
    val activeDevices = console.devices.count { !it.lastSeenAt.isNullOrBlank() }
    val recentDevices = console.devices
        .sortedByDescending { it.lastSeenAt.orEmpty() }
        .take(8)
    val pendingCommands = console.config?.pendingCommands ?: emptyList()

    PageShell(
        title = DesktopMessages.t(locale, "analytics.title"),
        description = DesktopMessages.t(locale, "analytics.remoteDescription"),
        badge = DesktopMessages.t(locale, "analytics.title"),
        locale = locale,
        actions = actions,
    ) {
        ErrorBanner(message = error, locale = locale)
        MetricRow(
            cards = listOf(
                {
                    MetricCard(
                        title = DesktopMessages.t(locale, "analytics.metric.cloudRevision"),
                        value = (console.config?.revision ?: 0L).toString(),
                        tone = MetricTone.INFO,
                        helper = DesktopMessages.t(locale, "analytics.metric.cloudRevisionHelper"),
                    )
                },
                {
                    MetricCard(
                        title = DesktopMessages.t(locale, "analytics.metric.smsCode"),
                        value = smsCode.toString(),
                        tone = MetricTone.SUCCESS,
                        helper = DesktopMessages.t(locale, "analytics.metric.smsCodeHelper"),
                    )
                },
                {
                    MetricCard(
                        title = DesktopMessages.t(locale, "analytics.metric.smsPlain"),
                        value = smsPlain.toString(),
                        helper = DesktopMessages.t(locale, "analytics.metric.smsPlainHelper"),
                    )
                },
                {
                    MetricCard(
                        title = DesktopMessages.t(locale, "analytics.metric.appNotify"),
                        value = appNotify.toString(),
                        helper = DesktopMessages.t(locale, "analytics.metric.appNotifyHelper"),
                    )
                },
                {
                    MetricCard(
                        title = DesktopMessages.t(locale, "analytics.metric.callNotify"),
                        value = callNotify.toString(),
                        tone = MetricTone.WARNING,
                        helper = DesktopMessages.t(locale, "analytics.metric.callNotifyHelper"),
                    )
                },
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SurfaceCard(
                title = DesktopMessages.t(locale, "analytics.auditTitle"),
                subtitle = console.selectedDeviceId?.let { deviceId ->
                    DesktopMessages.t(locale, "records.deviceBadge", mapOf("deviceId" to deviceId))
                } ?: DesktopMessages.t(locale, "analytics.auditSubtitle"),
            ) {
                if (pendingCommands.isEmpty()) {
                    EmptyCard(
                        title = DesktopMessages.t(locale, "analytics.auditEmptyTitle"),
                        message = DesktopMessages.t(locale, "analytics.auditEmptyMessage"),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        pendingCommands.forEach { command ->
                            PendingCommandCard(command = command, locale = locale)
                        }
                    }
                }
            }
            SurfaceCard(
                title = DesktopMessages.t(locale, "analytics.auditTrailTitle"),
                subtitle = console.selectedDeviceId?.let { deviceId ->
                    DesktopMessages.t(locale, "records.deviceBadge", mapOf("deviceId" to deviceId))
                },
            ) {
                if (auditLogs.isEmpty()) {
                    EmptyCard(
                        title = DesktopMessages.t(locale, "analytics.auditEmptyTitle"),
                        message = DesktopMessages.t(locale, "analytics.auditEmptyMessage"),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        auditLogs.forEach { log ->
                            AuditLogCard(log = log, locale = locale)
                        }
                    }
                }
            }
            SurfaceCard(
                title = DesktopMessages.t(locale, "analytics.deviceActivityTitle"),
                subtitle = DesktopMessages.t(
                    locale,
                    "analytics.deviceActivitySubtitle",
                    mapOf(
                        "total" to console.devices.size,
                        "active" to activeDevices,
                    ),
                ),
            ) {
                if (recentDevices.isEmpty()) {
                    EmptyCard(
                        title = DesktopMessages.t(locale, "analytics.deviceEmptyTitle"),
                        message = DesktopMessages.t(locale, "analytics.deviceEmptyMessage"),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        recentDevices.forEach { device ->
                            DeviceActivityCard(device = device, locale = locale)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingCommandCard(command: DeviceConfigCommandResponse, locale: DesktopLocale) {
    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RelayBadge(
                text = DesktopMessages.t(
                    locale,
                    "analytics.auditRevision",
                    mapOf("revision" to command.targetRevision),
                ),
                tone = RelayTone.ACCENT,
            )
            RelayBadge(text = command.status)
            if (command.actorId > 0) {
                RelayBadge(
                    text = DesktopMessages.t(
                        locale,
                        "analytics.auditActor",
                        mapOf("actorId" to command.actorId),
                    ),
                )
            }
        }
        Text(
            text = command.summary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = formatTimestamp(command.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}


@Composable
private fun AuditLogCard(log: DeviceConfigAuditLogItem, locale: DesktopLocale) {
    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RelayBadge(text = log.eventType, tone = RelayTone.MUTED)
            RelayBadge(text = log.actorType)
            if (log.actorId > 0) {
                RelayBadge(
                    text = DesktopMessages.t(
                        locale,
                        "analytics.auditActor",
                        mapOf("actorId" to log.actorId),
                    ),
                )
            }
        }
        Text(
            text = log.summary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = DesktopMessages.t(
                locale,
                "analytics.auditRevision",
                mapOf("revision" to log.revision),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = formatTimestamp(log.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DeviceActivityCard(device: DeviceItem, locale: DesktopLocale) {
    SurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = device.displayName.ifBlank { device.deviceName },
                style = MaterialTheme.typography.titleMedium,
            )
            RelayBadge(
                text = DesktopMessages.t(locale, if (device.enabled) "common.enabled" else "common.disabled"),
                tone = if (device.enabled) RelayTone.SUCCESS else RelayTone.WARNING,
            )
        }
        Text(
            text = listOf(
                device.platform,
                device.deviceModel.ifBlank { DesktopMessages.t(locale, "common.unknownModel") },
                device.appVersion.ifBlank { DesktopMessages.t(locale, "common.unknownVersion") },
            ).joinToString(" / "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = device.lastSeenAt?.takeIf { it.isNotBlank() }?.let {
                DesktopMessages.t(locale, "common.lastSeen", mapOf("time" to formatTimestamp(it)))
            } ?: DesktopMessages.t(locale, "common.noHeartbeatYet"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Audit trail page size, matching the Tauri page's getDeviceConfigAuditLogs(id, 30, 0). */
private const val AUDIT_LOG_LIMIT = 30
