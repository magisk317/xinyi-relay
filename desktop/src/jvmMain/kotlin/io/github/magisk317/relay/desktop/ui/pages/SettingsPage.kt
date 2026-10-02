package io.github.magisk317.relay.desktop.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
import io.github.magisk317.relay.desktop.session.DesktopSessionState
import io.github.magisk317.relay.desktop.ui.ActionButton
import io.github.magisk317.relay.desktop.ui.ActionTone
import io.github.magisk317.relay.desktop.ui.ConsoleMuted
import io.github.magisk317.relay.desktop.ui.ErrorBanner
import io.github.magisk317.relay.desktop.ui.LiveBadge
import io.github.magisk317.relay.desktop.ui.LoadingCard
import io.github.magisk317.relay.desktop.ui.NoticeCard
import io.github.magisk317.relay.desktop.ui.PageShell
import io.github.magisk317.relay.desktop.ui.SurfaceCard
import io.github.magisk317.relay.desktop.ui.formatTimestamp
import kotlinx.coroutines.launch

/**
 * Desktop port of the webUI settings page: the config state of the selected
 * device plus the admin password form. The webUI keeps structured editing on
 * the apps and senders pages, so this page only reports status.
 */
@Composable
fun SettingsPage(
    session: DesktopSessionState,
    console: DesktopConsoleState,
    feed: DesktopRealtimeFeed,
    locale: DesktopLocale,
) {
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    val actions: @Composable () -> Unit = {
        LiveBadge(connected = feed.connected, locale = locale)
        ActionButton(
            text = DesktopMessages.t(locale, "settings.refresh"),
            onClick = { scope.launch { runCatching { console.refreshConfig() } } },
            tone = ActionTone.NEUTRAL,
        )
    }

    if (console.loading && console.config == null && console.error.isBlank()) {
        PageShell(
            title = DesktopMessages.t(locale, "settings.title"),
            description = DesktopMessages.t(locale, "settings.description"),
            badge = DesktopMessages.t(locale, "settings.title"),
            locale = locale,
            actions = actions,
        ) {
            LoadingCard(
                title = DesktopMessages.t(locale, "settings.loadingTitle"),
                message = DesktopMessages.t(locale, "settings.loadingMessage"),
                locale = locale,
            )
        }
        return
    }

    PageShell(
        title = DesktopMessages.t(locale, "settings.title"),
        description = DesktopMessages.t(locale, "settings.remoteDescription"),
        badge = DesktopMessages.t(locale, "settings.title"),
        locale = locale,
        actions = actions,
    ) {
        ErrorBanner(message = console.error, locale = locale)
        if (notice.isNotBlank()) {
            NoticeCard(text = notice)
        }
        SurfaceCard(
            title = DesktopMessages.t(locale, "settings.cloudTitle"),
            subtitle = DesktopMessages.t(
                locale,
                "settings.cloudSubtitle",
                mapOf("revision" to (console.config?.revision ?: 0L)),
            ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val selectedDeviceId = console.selectedDeviceId
                val selectedName = console.devices
                    .firstOrNull { it.id == selectedDeviceId }
                    ?.deviceName
                    ?: DesktopMessages.t(locale, "common.unknown")
                Text(
                    text = if (selectedDeviceId != null) {
                        DesktopMessages.t(
                            locale,
                            "records.deviceBadge",
                            mapOf("deviceId" to selectedDeviceId),
                        ) + " · " + selectedName
                    } else {
                        DesktopMessages.t(locale, "common.none")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ConsoleMuted,
                )
                val config = console.config
                Text(
                    text = if (config != null) {
                        "pending commands: ${config.pendingCommands.size}"
                    } else {
                        DesktopMessages.t(locale, "common.none")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ConsoleMuted,
                )
                Text(
                    text = if (config != null) {
                        "updated: ${formatTimestamp(config.updatedAt)}"
                    } else {
                        DesktopMessages.t(locale, "common.none")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ConsoleMuted,
                )
                Text(
                    text = DesktopMessages.t(locale, "settings.remoteDescription"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ConsoleMuted,
                )
            }
        }
        SurfaceCard(
            title = DesktopMessages.t(locale, "settings.accountTitle"),
            subtitle = "${DesktopMessages.t(locale, "settings.accountSubtitle")} (${session.username})",
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { value -> currentPassword = value },
                    label = { Text(DesktopMessages.t(locale, "settings.accountCurrentPassword")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { value -> newPassword = value },
                    label = { Text(DesktopMessages.t(locale, "settings.accountNewPassword")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
            }
            ActionButton(
                text = DesktopMessages.t(locale, "settings.accountSavePassword"),
                onClick = {
                    notice = ""
                    val client = session.currentClient()
                    if (client == null) {
                        console.showError(DesktopMessages.t(locale, "common.saveFailed"))
                        return@ActionButton
                    }
                    scope.launch {
                        runCatching { client.changePassword(currentPassword, newPassword) }
                            .onSuccess {
                                currentPassword = ""
                                newPassword = ""
                                notice = DesktopMessages.t(locale, "settings.accountPasswordUpdated")
                            }
                            .onFailure { failure ->
                                console.showError(
                                    failure.message ?: DesktopMessages.t(locale, "common.saveFailed"),
                                )
                            }
                    }
                },
                modifier = Modifier.padding(top = 16.dp),
                tone = ActionTone.PRIMARY,
                enabled = !console.saving && currentPassword.isNotEmpty() && newPassword.isNotEmpty(),
            )
        }
    }
}
