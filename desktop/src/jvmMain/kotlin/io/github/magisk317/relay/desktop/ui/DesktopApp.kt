package io.github.magisk317.relay.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import io.github.magisk317.relay.desktop.core.network.OkHttpRemoteStore
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.LocalePreference
import io.github.magisk317.relay.desktop.i18n.LocaleSetting
import io.github.magisk317.relay.desktop.local.DatabaseTransferController
import io.github.magisk317.relay.desktop.local.DesktopDiagnosticsController
import io.github.magisk317.relay.desktop.local.DesktopLocalSyncController
import io.github.magisk317.relay.desktop.platform.AwtFileDialog
import io.github.magisk317.relay.desktop.platform.AwtLinkOpener
import io.github.magisk317.relay.desktop.platform.AwtNotifier
import io.github.magisk317.relay.desktop.platform.AwtTray
import io.github.magisk317.relay.desktop.platform.TrayAction
import io.github.magisk317.relay.desktop.platform.TrayMenu
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
import io.github.magisk317.relay.desktop.session.DesktopRunMode
import io.github.magisk317.relay.desktop.session.collectDiagnostics
import io.github.magisk317.relay.desktop.session.DesktopSessionState
import kotlinx.coroutines.delay

/**
 * Realtime event types that must not reach the system tray: the heartbeat
 * arrives every 25s while connected and would bury every meaningful event
 * under keep-alive noise. Everything else (records ingested, device
 * registered/revoked, config updates) is mutation-worthy and mirrors what
 * the webUI shows as an in-page event.
 */
private val QUIET_EVENT_TYPES = setOf("device.heartbeat")

/**
 * Desktop root: bootstraps the session, resolves the locale the way the webUI
 * does (explicit setting > backend language tag > system locale) and switches
 * between splash, login and the app shell.
 */
@Composable
fun DesktopApp(window: java.awt.Window, windowState: WindowState, onQuit: () -> Unit) {
    val session = remember { DesktopSessionState() }
    LaunchedEffect(Unit) { session.bootstrap() }
    val console = remember(session) { DesktopConsoleState(session) }
    val feed = remember(session) {
        DesktopRealtimeFeed(
            baseUrlProvider = { session.activeProfile?.baseUrl },
            tokenProvider = { session.currentClient()?.accessToken },
        )
    }
    LaunchedEffect(session.authenticated, session.runMode) {
        if (session.authenticated) {
            console.bootstrap()
            // The Local run mode has no remote monitor: the Rust shell's
            // monitor_tick returns early before touching the backend
            // (main.rs), and pages refresh off the mirror after local
            // writes instead of waiting for realtime events.
            if (session.runMode == DesktopRunMode.Local) feed.stop() else feed.start()
        } else {
            feed.stop()
        }
    }
    DisposableEffect(Unit) { onDispose { feed.stop() } }

    // Shell navigation lives here, hoisted out of the shell, so the tray menu
    // dispatches page jumps through the same state the sidebar drives.
    var route by remember { mutableStateOf(DesktopRoute.OVERVIEW) }
    var trayQuit by remember { mutableStateOf(false) }

    // Platform seams (parity §5): the tray icon (menu, left-click wake, and
    // the one shared entry balloons are displayed on), plus the OS browser
    // hand-off for console links the UI renders.
    val tray = remember { AwtTray() }
    val notifier = remember { AwtNotifier(sharedIcon = { tray.trayIcon }) }
    val linkOpener = remember { AwtLinkOpener() }
    DisposableEffect(Unit) {
        onDispose {
            notifier.close()
            tray.close()
        }
    }
    LaunchedEffect(feed.lastEvent) {
        val event = feed.lastEvent ?: return@LaunchedEffect
        if (event.type in QUIET_EVENT_TYPES) return@LaunchedEffect
        notifier.notify(
            title = "Xinyi Relay",
            body = "${event.type} @ ${event.time}",
        )
    }

    // Local mirror (parity §5): the composition root of `:desktop:core` /
    // `:desktop:data`, opened against the active profile's backend. The run
    // mode decides whether it participates at all — Local/Hybrid keep the
    // local SQLite store in step with the backend and report its status in the
    // shell footer; Remote (the Rust default) leaves the mirror closed and
    // every page on ConsoleClient. A profile switch, logout or mode switch
    // closes and re-opens it.
    val localSync = remember { DesktopLocalSyncController() }
    // Read routing (parity §5): the Local run mode answers page reads from
    // the mirror this controller owns; Remote/Hybrid keep the HTTP client.
    session.localStoreProvider = { localSync.store }
    val mirrorProfile = session.activeProfile
    LaunchedEffect(session.authenticated, mirrorProfile, session.runMode) {
        val client = session.currentClient()
        if (session.runMode.usesLocalMirror && session.authenticated && mirrorProfile != null && client != null) {
            val remote = OkHttpRemoteStore(baseUrl = mirrorProfile.baseUrl)
            client.accessToken?.let { remote.setAccessToken(it) }
            localSync.start(remote)
            // Re-read once the mirror is live: the shell's first bootstrap
            // may have raced the open, and the Rust shell re-bootstraps on
            // every mode switch the same way (main.rs, set_run_mode).
            console.bootstrap()
            while (true) {
                delay(LOCAL_SYNC_INTERVAL_MS)
                localSync.sync()
            }
        } else {
            localSync.stop()
            console.bootstrap()
        }
    }
    DisposableEffect(Unit) { onDispose { localSync.stop() } }

    // Database transfer (parity §5): export/import of the local mirror. It
    // rides the mirror's connection through [DesktopLocalSyncController], so
    // the Remote run mode (mirror closed) yields the unavailable state on the
    // Advanced page instead of a second database.
    val transfer = remember {
        DatabaseTransferController(
            databaseProvider = { localSync.database },
            fileDialog = AwtFileDialog(),
        )
    }

    // Diagnostics export (parity §5): a token-free snapshot of profiles,
    // connection, run mode and mirror status for bug reports. Unlike the
    // database snapshot it needs no open mirror, so it stays usable in
    // Remote mode and while the mirror is the broken thing.
    val diagnostics = remember {
        DesktopDiagnosticsController(
            report = { collectDiagnostics(session, localSync.status) },
            fileDialog = AwtFileDialog(),
        )
    }

    var localeSetting by remember { mutableStateOf(LocalePreference.load()) }
    LaunchedEffect(localeSetting) { LocalePreference.save(localeSetting) }

    var locale by remember(localeSetting, session.serverLanguageTag) {
        mutableStateOf(LocalePreference.resolve(localeSetting, session.serverLanguageTag))
    }

    // Tray menu (parity §5): the Tauri tray's rows in its order, installed
    // once and re-labelled on a locale switch. A machine with no system tray
    // (no notification area daemon, headless) simply gets no icon - the
    // window stays the only surface, and [DesktopTray.installed] says so.
    LaunchedEffect(locale) {
        val items = TrayMenu.items { action ->
            DesktopMessages.t(locale, "platform.tray.${action.messageKey}")
        }
        if (tray.installed) {
            tray.updateItems(items)
        } else {
            tray.install(
                items = items,
                onSelect = { action ->
                    when (action) {
                        TrayAction.SHOW -> Unit
                        TrayAction.OVERVIEW -> route = DesktopRoute.OVERVIEW
                        // The KMP shell folds the webUI's device page into
                        // Advanced (pairing code + device list live there).
                        TrayAction.DEVICES -> route = DesktopRoute.ADVANCED
                        TrayAction.RECORDS -> route = DesktopRoute.RECORDS
                        TrayAction.RESTART_MONITOR -> {
                            feed.stop()
                            feed.start()
                        }
                        TrayAction.QUIT -> trayQuit = true
                    }
                    // Page jumps and reconnects are only useful with the
                    // window in front; quit leaves the machine alone.
                    if (action != TrayAction.QUIT) wake(window, windowState)
                },
                onWake = { wake(window, windowState) },
            )
        }
    }

    XinyiDesktopTheme {
        // The tray's quit row lands here: [onQuit] stops the application
        // scope during composition, which runs the disposals above (tray icon
        // removal, feed stop) on the way out.
        if (trayQuit) onQuit()
        Surface(modifier = Modifier.fillMaxSize()) {
            when {
                session.loading -> SplashScreen(locale = locale)
                !session.authenticated -> LoginScreen(session = session, locale = locale)
                else -> AppShellScreen(
                    session = session,
                    console = console,
                    feed = feed,
                    linkOpener = linkOpener,
                    localSync = localSync.status,
                    transfer = transfer,
                    diagnostics = diagnostics,
                    locale = locale,
                    route = route,
                    onNavigate = { route = it },
                    selectedLocale = localeSetting,
                    onLocaleChange = { localeSetting = it },
                )
            }
        }
    }
}

@Composable
private fun SplashScreen(locale: DesktopLocale) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = DesktopMessages.t(locale, "app.embeddedConnecting"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Cadence of the periodic mirror re-sync; matches
 * `SyncThrottler.DEFAULT_INTERVAL_MILLIS`. The controller drives its own tick
 * and forces each round, so the footer counts always describe the round that
 * just ran.
 */
private const val LOCAL_SYNC_INTERVAL_MS = 5 * 60 * 1000L

/**
 * Brings the shell to the front: unhide, un-minimise, focus. The tray's
 * left-click wake and every menu row except quit land here. The un-minimise
 * goes through [WindowState.isMinimized] so Compose's own window update path
 * applies it, rather than poking the underlying frame behind its back.
 */
private fun wake(window: java.awt.Window, windowState: WindowState) {
    window.isVisible = true
    windowState.isMinimized = false
    window.toFront()
}
