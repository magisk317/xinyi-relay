package io.github.magisk317.relay.desktop.ui.pages

import androidx.compose.runtime.Composable
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
import io.github.magisk317.relay.desktop.session.DesktopSessionState
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.ui.DesktopPage

/**
 * Interim frames for the pages not ported yet; each one is replaced by the
 * real page as C3.3 lands it, keeping the shell navigable in between.
 */
@Composable
fun SendersPage(session: DesktopSessionState, console: DesktopConsoleState, feed: DesktopRealtimeFeed, locale: DesktopLocale) {
    PageFrame(locale = locale, titleKey = "senders.title", descriptionKey = "senders.description")
}

@Composable
fun SettingsPage(session: DesktopSessionState, console: DesktopConsoleState, feed: DesktopRealtimeFeed, locale: DesktopLocale) {
    PageFrame(locale = locale, titleKey = "settings.title", descriptionKey = "settings.description")
}

@Composable
fun AdvancedPage(session: DesktopSessionState, console: DesktopConsoleState, feed: DesktopRealtimeFeed, locale: DesktopLocale) {
    PageFrame(locale = locale, titleKey = "advanced.title", descriptionKey = "advanced.description")
}

@Composable
fun ScheduledTasksPage(session: DesktopSessionState, console: DesktopConsoleState, feed: DesktopRealtimeFeed, locale: DesktopLocale) {
    PageFrame(locale = locale, titleKey = "scheduledTasks.title", descriptionKey = "scheduledTasks.description")
}

@Composable
private fun PageFrame(locale: DesktopLocale, titleKey: String, descriptionKey: String) {
    DesktopPage(
        title = DesktopMessages.t(locale, titleKey),
        description = DesktopMessages.t(locale, descriptionKey),
    ) {}
}
