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
import io.github.magisk317.relay.desktop.core.network.OkHttpRemoteStore
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.LocalePreference
import io.github.magisk317.relay.desktop.i18n.LocaleSetting
import io.github.magisk317.relay.desktop.local.DesktopLocalSyncController
import io.github.magisk317.relay.desktop.platform.AwtLinkOpener
import io.github.magisk317.relay.desktop.platform.AwtNotifier
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
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
fun DesktopApp() {
    val session = remember { DesktopSessionState() }
    LaunchedEffect(Unit) { session.bootstrap() }
    val console = remember(session) { DesktopConsoleState(session) }
    val feed = remember(session) {
        DesktopRealtimeFeed(
            baseUrlProvider = { session.activeProfile?.baseUrl },
            tokenProvider = { session.currentClient()?.accessToken },
        )
    }
    LaunchedEffect(session.authenticated) {
        if (session.authenticated) {
            console.bootstrap()
            feed.start()
        } else {
            feed.stop()
        }
    }
    DisposableEffect(Unit) { onDispose { feed.stop() } }

    // Platform seams (parity §5): tray delivery for events the user should
    // notice while the window is in the background, and the OS browser
    // hand-off for console links the UI renders.
    val notifier = remember { AwtNotifier() }
    val linkOpener = remember { AwtLinkOpener() }
    DisposableEffect(Unit) { onDispose { notifier.close() } }
    LaunchedEffect(feed.lastEvent) {
        val event = feed.lastEvent ?: return@LaunchedEffect
        if (event.type in QUIET_EVENT_TYPES) return@LaunchedEffect
        notifier.notify(
            title = "Xinyi Relay",
            body = "${event.type} @ ${event.time}",
        )
    }

    // Local mirror (parity §5): the composition root of `:desktop:core` /
    // `:desktop:data`, opened against the active profile's backend. The console
    // pages still read through ConsoleClient (Remote mode); the mirror keeps the
    // local SQLite store in step with the backend and reports its status in the
    // shell footer. A profile switch or logout closes and re-opens it.
    val localSync = remember { DesktopLocalSyncController() }
    val mirrorProfile = session.activeProfile
    LaunchedEffect(session.authenticated, mirrorProfile) {
        val client = session.currentClient()
        if (session.authenticated && mirrorProfile != null && client != null) {
            val remote = OkHttpRemoteStore(baseUrl = mirrorProfile.baseUrl)
            client.accessToken?.let { remote.setAccessToken(it) }
            localSync.start(remote)
            while (true) {
                delay(LOCAL_SYNC_INTERVAL_MS)
                localSync.sync()
            }
        } else {
            localSync.stop()
        }
    }
    DisposableEffect(Unit) { onDispose { localSync.stop() } }

    var localeSetting by remember { mutableStateOf(LocalePreference.load()) }
    LaunchedEffect(localeSetting) { LocalePreference.save(localeSetting) }

    var locale by remember(localeSetting, session.serverLanguageTag) {
        mutableStateOf(LocalePreference.resolve(localeSetting, session.serverLanguageTag))
    }

    XinyiDesktopTheme {
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
                    locale = locale,
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
