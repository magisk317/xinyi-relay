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
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.remote.DeviceActionCommandItem
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
import io.github.magisk317.relay.desktop.session.DesktopSessionState
import io.github.magisk317.relay.desktop.ui.ActionButton
import io.github.magisk317.relay.desktop.ui.ActionTone
import io.github.magisk317.relay.desktop.ui.EmptyCard
import io.github.magisk317.relay.desktop.ui.ErrorBanner
import io.github.magisk317.relay.desktop.ui.LiveBadge
import io.github.magisk317.relay.desktop.ui.LoadingCard
import io.github.magisk317.relay.desktop.ui.NoticeCard
import io.github.magisk317.relay.desktop.ui.PageShell
import io.github.magisk317.relay.desktop.ui.RelayBadge
import io.github.magisk317.relay.desktop.ui.RelayOption
import io.github.magisk317.relay.desktop.ui.RelaySelect
import io.github.magisk317.relay.desktop.ui.RelayTone
import io.github.magisk317.relay.desktop.ui.SurfaceCard
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun DeviceControlPage(
    session: DesktopSessionState,
    console: DesktopConsoleState,
    feed: DesktopRealtimeFeed,
    locale: DesktopLocale,
) {
    val scope = rememberCoroutineScope()
    var actions by remember { mutableStateOf<List<DeviceActionCommandItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    val deviceId = console.selectedDeviceId
    val modeAllowsCommands = session.runMode.allowsRemoteDeviceActions

    LaunchedEffect(deviceId, session.runMode, session.authenticated, refreshKey) {
        val client = if (modeAllowsCommands && session.authenticated) session.currentClient() else null
        if (deviceId == null || client == null) {
            actions = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        runCatching { client.listDeviceActions(deviceId) }
            .onSuccess {
                actions = it.actions
                error = ""
            }
            .onFailure { failure -> error = failure.message ?: DesktopMessages.t(locale, "common.loadFailed") }
        loading = false
    }

    LaunchedEffect(feed.lastEvent) {
        if (feed.lastEvent?.type == "device.action.updated") refreshKey += 1
    }

    val deviceOptions = console.devices.map { device ->
        RelayOption(
            value = device.id.toString(),
            label = device.displayName.ifBlank { device.deviceName },
            description = "${device.platform} / ${device.deviceModel.ifBlank { DesktopMessages.t(locale, "common.unknownModel") }}",
        )
    }
    val canSubmit = modeAllowsCommands && session.authenticated && session.currentClient() != null &&
        deviceId != null && !submitting

    val headerActions: @Composable () -> Unit = {
        LiveBadge(connected = feed.connected, locale = locale)
        if (deviceOptions.isNotEmpty()) {
            RelaySelect(
                value = deviceId?.toString().orEmpty(),
                options = deviceOptions,
                onValueChange = { value -> value.toLongOrNull()?.let(console::selectDevice) },
            )
        }
        ActionButton(
            text = DesktopMessages.t(locale, "deviceControl.refresh"),
            onClick = {
                scope.launch {
                    runCatching { console.refreshDevices() }
                        .onFailure { failure -> error = failure.message ?: DesktopMessages.t(locale, "common.loadFailed") }
                    refreshKey += 1
                }
            },
            tone = ActionTone.NEUTRAL,
        )
    }

    PageShell(
        title = DesktopMessages.t(locale, "deviceControl.title"),
        description = DesktopMessages.t(locale, "deviceControl.description"),
        badge = DesktopMessages.t(locale, "layout.nav.devices"),
        locale = locale,
        actions = headerActions,
    ) {
        ErrorBanner(message = error, locale = locale)

        if (!modeAllowsCommands) {
            NoticeCard(DesktopMessages.t(locale, "deviceControl.remoteOnly"))
        } else if (!session.authenticated) {
            NoticeCard(DesktopMessages.t(locale, "deviceControl.signInHint"))
        }

        if (deviceId == null) {
            EmptyCard(
                title = DesktopMessages.t(locale, "deviceControl.noDevice"),
                message = DesktopMessages.t(locale, "deviceControl.noDeviceHint"),
            )
            return@PageShell
        }

        SurfaceCard(
            title = DesktopMessages.t(locale, "deviceControl.actionsTitle"),
            subtitle = DesktopMessages.t(locale, "deviceControl.actionsSubtitle"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DESKTOP_DEVICE_CONTROL_ACTIONS.forEachIndexed { index, action ->
                        ActionButton(
                            text = DesktopMessages.t(locale, action.labelKey),
                            onClick = {
                                val client = if (session.runMode.allowsRemoteDeviceActions) session.currentClient() else null
                                if (client == null) {
                                    error = DesktopMessages.t(locale, "deviceControl.remoteOnly")
                                } else {
                                    scope.launch {
                                        submitting = true
                                        runCatching { client.queueDeviceAction(deviceId, action.action, action.params) }
                                            .onSuccess { command ->
                                                actions = listOf(command) + actions.filter { it.id != command.id }
                                                error = ""
                                            }
                                            .onFailure { failure ->
                                                error = failure.message ?: DesktopMessages.t(locale, "common.createFailed")
                                            }
                                        submitting = false
                                    }
                                }
                            },
                            tone = if (index == 0) ActionTone.PRIMARY else ActionTone.NEUTRAL,
                            enabled = canSubmit,
                        )
                    }
                }
                Text(
                    text = DesktopMessages.t(locale, "deviceControl.restoreRingingHint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SurfaceCard(
            title = DesktopMessages.t(locale, "deviceControl.historyTitle"),
            subtitle = DesktopMessages.t(locale, "deviceControl.historySubtitle"),
        ) {
            when {
                !modeAllowsCommands || !session.authenticated -> Text(
                    text = DesktopMessages.t(locale, "deviceControl.historyUnavailable"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                loading && actions.isEmpty() -> LoadingCard(
                    title = DesktopMessages.t(locale, "deviceControl.historyTitle"),
                    message = DesktopMessages.t(locale, "common.loadingMessage"),
                    locale = locale,
                )
                actions.isEmpty() -> EmptyCard(
                    title = DesktopMessages.t(locale, "deviceControl.historyEmpty"),
                    message = DesktopMessages.t(locale, "deviceControl.historyEmptyMessage"),
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { item -> DeviceActionRow(item = item, locale = locale) }
                }
            }
        }
    }
}

@Composable
private fun DeviceActionRow(item: DeviceActionCommandItem, locale: DesktopLocale) {
    val status = item.status.orEmpty()
    val statusKey = when (status) {
        "pending" -> "deviceControl.status.pending"
        "done" -> "deviceControl.status.done"
        "failed" -> "deviceControl.status.failed"
        else -> "common.unknown"
    }
    val statusTone = when (status) {
        "pending" -> RelayTone.ACCENT
        "done" -> RelayTone.SUCCESS
        "failed" -> RelayTone.DANGER
        else -> RelayTone.MUTED
    }
    val summaryKey = when (item.action) {
        "SET_RINGER_MODE" -> when (item.params["mode"]?.jsonPrimitive?.content) {
            "normal" -> "deviceControl.action.ring"
            "silent" -> "deviceControl.action.silent"
            "vibrate" -> "deviceControl.action.vibrate"
            else -> "deviceControl.action.unknown"
        }
        "SET_INTERRUPTION_FILTER" -> when (item.params["filter"]?.jsonPrimitive?.content) {
            "priority" -> "deviceControl.action.dnd"
            "all" -> "deviceControl.action.allowAll"
            else -> "deviceControl.action.unknown"
        }
        else -> "deviceControl.action.unknown"
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = DesktopMessages.t(locale, summaryKey),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            RelayBadge(text = DesktopMessages.t(locale, statusKey), tone = statusTone)
        }
        val details = buildList {
            item.createdAt?.let { add(it) }
            item.failureReason?.let { add(DesktopMessages.t(locale, "deviceControl.failureHint", mapOf("reason" to it))) }
        }.joinToString(" · ")
        if (details.isNotBlank()) {
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
