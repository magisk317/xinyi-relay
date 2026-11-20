package io.github.magisk317.relay.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
import io.github.magisk317.relay.desktop.ui.PageOutlet
import io.github.magisk317.relay.desktop.ui.pages.AdvancedPage
import io.github.magisk317.relay.desktop.ui.pages.AdvancedPageSection
import io.github.magisk317.relay.desktop.ui.pages.AnalyticsPage
import io.github.magisk317.relay.desktop.ui.pages.AppsPage
import io.github.magisk317.relay.desktop.ui.pages.DeviceControlPage
import io.github.magisk317.relay.desktop.ui.pages.OverviewPage
import io.github.magisk317.relay.desktop.ui.pages.RecordsPage
import io.github.magisk317.relay.desktop.ui.pages.ScheduledTasksPage
import io.github.magisk317.relay.desktop.ui.pages.SendersPage
import io.github.magisk317.relay.desktop.ui.pages.SettingsPage
import io.github.magisk317.relay.desktop.i18n.LocaleSetting
import io.github.magisk317.relay.desktop.local.DatabaseTransferController
import io.github.magisk317.relay.desktop.local.DesktopDiagnosticsController
import io.github.magisk317.relay.desktop.local.DesktopLocalSyncController
import io.github.magisk317.relay.desktop.local.LocalSyncStatus
import io.github.magisk317.relay.desktop.platform.DesktopLinkOpener
import io.github.magisk317.relay.desktop.session.DesktopSessionState
import kotlinx.coroutines.delay

/** Routes presented in the shared Web/Desktop console information architecture. */
enum class DesktopRoute(val labelKey: String, val icon: ImageVector) {
    OVERVIEW("layout.nav.overview", Icons.Filled.Home),
    ANALYTICS("layout.nav.analytics", Icons.Filled.Star),
    APPS("layout.nav.apps", Icons.Filled.Apps),
    SENDERS("layout.nav.senders", Icons.AutoMirrored.Filled.Send),
    SCHEDULED_TASKS("layout.nav.scheduled_tasks", Icons.Filled.Schedule),
    RECORDS("layout.nav.records", Icons.Filled.Inbox),
    DEVICES("layout.nav.devices", Icons.Filled.Apps),
    DEVICE_CONTROL("layout.nav.deviceControl", Icons.Filled.Build),
    DIAGNOSTICS("layout.nav.diagnostics", Icons.Filled.Build),
    SETTINGS("layout.nav.settings", Icons.Filled.Settings),
}

internal data class DesktopNavGroup(val labelKey: String, val routes: List<DesktopRoute>)

internal val DESKTOP_NAV_GROUPS = listOf(
    DesktopNavGroup("layout.nav.group.overview", listOf(DesktopRoute.OVERVIEW, DesktopRoute.ANALYTICS)),
    DesktopNavGroup("layout.nav.group.automation", listOf(DesktopRoute.APPS, DesktopRoute.SENDERS, DesktopRoute.SCHEDULED_TASKS)),
    DesktopNavGroup("layout.nav.group.records", listOf(DesktopRoute.RECORDS)),
    DesktopNavGroup("layout.nav.group.devices", listOf(DesktopRoute.DEVICES, DesktopRoute.DEVICE_CONTROL)),
    DesktopNavGroup("layout.nav.group.diagnostics", listOf(DesktopRoute.DIAGNOSTICS)),
    DesktopNavGroup("layout.nav.group.settings", listOf(DesktopRoute.SETTINGS)),
)

/**
 * Desktop port of the webUI AppLayout: header with account menu, sidebar
 * navigation, page outlet and footer hint. Mobile tab bar is dropped — a
 * desktop window always has room for the sidebar.
 */
@Composable
fun AppShellScreen(
    session: DesktopSessionState,
    console: DesktopConsoleState,
    feed: DesktopRealtimeFeed,
    linkOpener: DesktopLinkOpener,
    localSync: LocalSyncStatus,
    localSyncController: DesktopLocalSyncController?,
    localServerUrl: String?,
    transfer: DatabaseTransferController,
    diagnostics: DesktopDiagnosticsController,
    locale: DesktopLocale,
    route: DesktopRoute,
    onNavigate: (DesktopRoute) -> Unit,
    onLocaleChange: (LocaleSetting) -> Unit,
    selectedLocale: LocaleSetting,
    modifier: Modifier = Modifier,
) {

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                session = session,
                linkOpener = linkOpener,
                locale = locale,
                selectedLocale = selectedLocale,
                onLocaleChange = onLocaleChange,
            )
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Sidebar(
                    route = route,
                    locale = locale,
                    onSelect = onNavigate,
                )
                Column(
                    modifier = Modifier.fillMaxSize().padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        RouteContent(
                            route = route,
                            session = session,
                            console = console,
                            feed = feed,
                            transfer = transfer,
                            diagnostics = diagnostics,
                            localSyncController = localSyncController,
                            localServerUrl = localServerUrl,
                            locale = locale,
                            onNavigate = onNavigate,
                        )
                    }
                    Footer(locale = locale, localSync = localSync)
                }
            }
        }
    }
}

@Composable
// Compose Resources are not wired for this module (no composeResources source set anywhere in the repo), so the classpath-resource overload stays; suppressed here rather than file-wide so every other deprecation in this file is still reported.
@Suppress("DEPRECATION")
private fun Header(
    session: DesktopSessionState,
    linkOpener: DesktopLinkOpener,
    locale: DesktopLocale,
    selectedLocale: LocaleSetting,
    onLocaleChange: (LocaleSetting) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource("icon.png"),
                contentDescription = "Xinyi Relay Logo",
                modifier = Modifier.size(36.dp),
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = DesktopMessages.t(locale, "login.title"),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Desktop",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            ConsoleLinkButton(
                session = session,
                locale = locale,
                linkOpener = linkOpener,
            )
            AccountMenu(
                session = session,
                locale = locale,
                selectedLocale = selectedLocale,
                onLocaleChange = onLocaleChange,
            )
        }
    }
}

@Composable
private fun Sidebar(
    route: DesktopRoute,
    locale: DesktopLocale,
    onSelect: (DesktopRoute) -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(min = 220.dp, max = 260.dp).fillMaxHeight(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DESKTOP_NAV_GROUPS.forEach { group ->
                Text(
                    text = DesktopMessages.t(locale, group.labelKey).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 2.dp),
                )
                group.routes.forEach { item ->
                    val active = item == route
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .background(
                                if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                MaterialTheme.shapes.extraSmall,
                            )
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            text = DesktopMessages.t(locale, item.labelKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountMenu(
    session: DesktopSessionState,
    locale: DesktopLocale,
    selectedLocale: LocaleSetting,
    onLocaleChange: (LocaleSetting) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            modifier = Modifier.clickable { expanded = true },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.username.ifBlank { "—" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = if (expanded) "▴" else "▾",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = DesktopMessages.t(locale, "layout.language"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LocaleSetting.entries.forEach { candidate ->
                val selected = candidate == selectedLocale
                DropdownMenuItem(
                    text = {
                        Text(
                            text = DesktopMessages.t(locale, "locale.${candidate.tag}"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onLocaleChange(candidate)
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = DesktopMessages.t(locale, "layout.logout"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                onClick = {
                    expanded = false
                    session.logout()
                },
            )
        }
    }
}

@Composable
private fun Footer(locale: DesktopLocale, localSync: LocalSyncStatus? = null) {
    Surface(color = Color.Transparent) {
        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            Text(
                text = "Xinyi Relay Desktop",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "  /  " + DesktopMessages.t(locale, "layout.footerHint"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            localSync?.let { LocalMirrorHint(status = it, locale = locale) }
        }
    }
}

@Composable
private fun RouteContent(
    route: DesktopRoute,
    session: DesktopSessionState,
    console: DesktopConsoleState,
    feed: DesktopRealtimeFeed,
    transfer: DatabaseTransferController,
    diagnostics: DesktopDiagnosticsController,
    localSyncController: DesktopLocalSyncController?,
    localServerUrl: String?,
    locale: DesktopLocale,
    onNavigate: (DesktopRoute) -> Unit = {},
) {
    PageOutlet {
        when (route) {
            DesktopRoute.OVERVIEW -> OverviewPage(session, console, feed, locale)
            DesktopRoute.ANALYTICS -> AnalyticsPage(session, console, feed, locale)
            DesktopRoute.APPS -> AppsPage(session, console, feed, locale)
            DesktopRoute.RECORDS -> RecordsPage(session, console, feed, locale)
            DesktopRoute.SENDERS -> SendersPage(session, console, feed, locale)
            DesktopRoute.SETTINGS -> SettingsPage(session, console, feed, locale)
            DesktopRoute.DEVICES -> AdvancedPage(
                session = session,
                console = console,
                feed = feed,
                transfer = transfer,
                diagnostics = diagnostics,
                localSyncController = localSyncController,
                localServerUrl = localServerUrl,
                locale = locale,
                section = AdvancedPageSection.DEVICES,
            )
            DesktopRoute.DEVICE_CONTROL -> DeviceControlPage(session, console, feed, locale)
            DesktopRoute.DIAGNOSTICS -> AdvancedPage(
                session = session,
                console = console,
                feed = feed,
                transfer = transfer,
                diagnostics = diagnostics,
                localSyncController = localSyncController,
                localServerUrl = localServerUrl,
                locale = locale,
                section = AdvancedPageSection.DIAGNOSTICS,
            )
            DesktopRoute.SCHEDULED_TASKS -> ScheduledTasksPage(session, console, feed, locale)
        }
    }
}

/**
 * Header affordance for the desktop link-opening seam: the webUI hands link
 * activation to the browser chrome, the shell has no chrome, so the console
 * URL goes through [DesktopLinkOpener] (http/https only, everything else is
 * refused). A failed hand-off surfaces as a transient hint instead of a
 * silent no-op.
 */
@Composable
private fun ConsoleLinkButton(
    session: DesktopSessionState,
    locale: DesktopLocale,
    linkOpener: DesktopLinkOpener,
) {
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hint) {
        if (hint != null) {
            delay(LINK_HINT_MS)
            hint = null
        }
    }
    val consoleUrl = session.activeProfile?.baseUrl?.trim().orEmpty()
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = {
                hint = if (consoleUrl.isNotEmpty() && linkOpener.open(consoleUrl)) {
                    null
                } else {
                    DesktopMessages.t(locale, "platform.openLinkFailed")
                }
            },
            enabled = consoleUrl.isNotEmpty(),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(text = DesktopMessages.t(locale, "platform.openConsole"))
        }
        hint?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * Footer read-out of the local mirror (parity §5): one line saying whether the
 * SQLite mirror is syncing, failed, or how many rows the last round touched.
 * Renders nothing until a round has completed, so pre-login states stay blank.
 */
@Composable
private fun LocalMirrorHint(status: LocalSyncStatus, locale: DesktopLocale) {
    val text = when {
        status.error != null -> DesktopMessages.t(locale, "local.mirror.failed")
        status.serverError != null -> DesktopMessages.t(locale, "local.mirror.serverFailed")
        status.syncing -> DesktopMessages.t(locale, "local.mirror.syncing")
        status.established -> DesktopMessages.t(
            locale = locale,
            key = "local.mirror.summary",
            params = mapOf("devices" to status.devices, "records" to status.records),
        )
        else -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp),
    )
}

private const val LINK_HINT_MS = 3_000L
