package io.github.magisk317.relay.desktop.ui.pages

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
import io.github.magisk317.relay.desktop.session.DesktopSessionState
import io.github.magisk317.relay.desktop.ui.ActionButton
import io.github.magisk317.relay.desktop.ui.ActionTone
import io.github.magisk317.relay.desktop.ui.NoticeCard
import io.github.magisk317.relay.desktop.ui.PageShell
import io.github.magisk317.relay.desktop.ui.SurfaceCard

/**
 * Desktop port of the webUI scheduled-tasks page. The page is informational on
 * the phone side, so the desktop build keeps it as an entry point that says so;
 * the browser alert() becomes an in-page notice card.
 */
@Composable
fun ScheduledTasksPage(
    session: DesktopSessionState,
    console: DesktopConsoleState,
    feed: DesktopRealtimeFeed,
    locale: DesktopLocale,
) {
    var notice by remember { mutableStateOf("") }
    PageShell(
        title = DesktopMessages.t(locale, "scheduledTasks.title"),
        description = DesktopMessages.t(locale, "scheduledTasks.description"),
        locale = locale,
    ) {
        if (notice.isNotBlank()) {
            NoticeCard(text = notice)
        }
        SurfaceCard(
            title = DesktopMessages.t(locale, "scheduledTasks.listTitle"),
            subtitle = DesktopMessages.t(locale, "scheduledTasks.emptySubtitle"),
        ) {
            ActionButton(
                text = DesktopMessages.t(locale, "scheduledTasks.addAction"),
                onClick = { notice = DesktopMessages.t(locale, "scheduledTasks.mobileOnlyHint") },
                tone = ActionTone.PRIMARY,
            )
        }
    }
}

