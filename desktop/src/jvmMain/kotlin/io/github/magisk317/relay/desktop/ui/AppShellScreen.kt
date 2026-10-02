package io.github.magisk317.relay.desktop.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.LocaleSetting
import io.github.magisk317.relay.desktop.session.DesktopSessionState

/** Routes mirroring the webUI router, in sidebar order. */
enum class DesktopRoute(val labelKey: String, val icon: ImageVector) {
    OVERVIEW("layout.nav.overview", Icons.Filled.Home),
    ANALYTICS("layout.nav.analytics", Icons.Filled.Star),
    APPS("layout.nav.apps", Icons.Filled.Apps),
    RECORDS("layout.nav.records", Icons.Filled.Inbox),
    SENDERS("layout.nav.senders", Icons.AutoMirrored.Filled.Send),
    SETTINGS("layout.nav.settings", Icons.Filled.Settings),
    ADVANCED("layout.nav.advanced", Icons.Filled.Build),
    SCHEDULED_TASKS("layout.nav.scheduled_tasks", Icons.Filled.Schedule),
}

/**
 * Desktop port of the webUI AppLayout: header with account menu, sidebar
 * navigation, page outlet and footer hint. Mobile tab bar is dropped — a
 * desktop window always has room for the sidebar.
 */
@Composable
fun AppShellScreen(
    session: DesktopSessionState,
    locale: DesktopLocale,
    onLocaleChange: (LocaleSetting) -> Unit,
    selectedLocale: LocaleSetting,
    modifier: Modifier = Modifier,
) {
    var route by remember { mutableStateOf(DesktopRoute.OVERVIEW) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                session = session,
                locale = locale,
                selectedLocale = selectedLocale,
                onLocaleChange = onLocaleChange,
            )
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Sidebar(
                    route = route,
                    locale = locale,
                    onSelect = { route = it },
                )
                Column(
                    modifier = Modifier.fillMaxSize().padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        RouteContent(route = route, locale = locale)
                    }
                    Footer(locale = locale)
                }
            }
        }
    }
}

@Composable
private fun Header(
    session: DesktopSessionState,
    locale: DesktopLocale,
    selectedLocale: LocaleSetting,
    onLocaleChange: (LocaleSetting) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "XC",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = DesktopMessages.t(locale, "login.title"),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Desktop",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
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
        modifier = Modifier.widthIn(min = 220.dp).fillMaxHeight(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DesktopRoute.entries.forEach { item ->
                val active = item == route
                val background = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                }
                val content = if (active) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(item) },
                    shape = MaterialTheme.shapes.medium,
                    color = background,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = content,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = DesktopMessages.t(locale, item.labelKey),
                            style = MaterialTheme.typography.titleMedium,
                            color = content,
                            modifier = Modifier.padding(start = 12.dp),
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
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.username.ifBlank { "—" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                )
                Text(
                    text = if (expanded) "▴" else "▾",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
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
private fun Footer(locale: DesktopLocale) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
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
        }
    }
}

@Composable
private fun RouteContent(route: DesktopRoute, locale: DesktopLocale) {
    when (route) {
        DesktopRoute.OVERVIEW -> OverviewPage(locale)
        DesktopRoute.ANALYTICS -> AnalyticsPage(locale)
        DesktopRoute.APPS -> AppsPage(locale)
        DesktopRoute.RECORDS -> RecordsPage(locale)
        DesktopRoute.SENDERS -> SendersPage(locale)
        DesktopRoute.SETTINGS -> SettingsPage(locale)
        DesktopRoute.ADVANCED -> AdvancedPage(locale)
        DesktopRoute.SCHEDULED_TASKS -> ScheduledTasksPage(locale)
    }
}

/** Placeholder pages: frame with title + description until each page is ported. */
@Composable
private fun OverviewPage(locale: DesktopLocale) {
    PageFrame(
        locale = locale,
        titleKey = "overview.title",
        descriptionKey = "overview.description",
    )
}

@Composable
private fun AnalyticsPage(locale: DesktopLocale) {
    PageFrame(
        locale = locale,
        titleKey = "analytics.title",
        descriptionKey = "analytics.description",
    )
}

@Composable
private fun AppsPage(locale: DesktopLocale) {
    PageFrame(
        locale = locale,
        titleKey = "apps.title",
        descriptionKey = "apps.description",
    )
}

@Composable
private fun RecordsPage(locale: DesktopLocale) {
    PageFrame(
        locale = locale,
        titleKey = "records.title",
        descriptionKey = "records.description",
    )
}

@Composable
private fun SendersPage(locale: DesktopLocale) {
    PageFrame(
        locale = locale,
        titleKey = "senders.title",
        descriptionKey = "senders.description",
    )
}

@Composable
private fun SettingsPage(locale: DesktopLocale) {
    PageFrame(
        locale = locale,
        titleKey = "settings.title",
        descriptionKey = "settings.description",
    )
}

@Composable
private fun AdvancedPage(locale: DesktopLocale) {
    PageFrame(
        locale = locale,
        titleKey = "advanced.title",
        descriptionKey = "advanced.description",
    )
}

@Composable
private fun ScheduledTasksPage(locale: DesktopLocale) {
    PageFrame(
        locale = locale,
        titleKey = "scheduledTasks.title",
        descriptionKey = "scheduledTasks.description",
    )
}

@Composable
private fun PageFrame(locale: DesktopLocale, titleKey: String, descriptionKey: String) {
    DesktopPage(
        title = DesktopMessages.t(locale, titleKey),
        description = DesktopMessages.t(locale, descriptionKey),
    ) {
        DesktopPagePlaceholder()
    }
}
