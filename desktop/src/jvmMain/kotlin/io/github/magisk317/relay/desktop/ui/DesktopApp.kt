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
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.i18n.LocalePreference
import io.github.magisk317.relay.desktop.i18n.LocaleSetting
import io.github.magisk317.relay.desktop.remote.DesktopRealtimeFeed
import io.github.magisk317.relay.desktop.session.DesktopConsoleState
import io.github.magisk317.relay.desktop.session.DesktopSessionState

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
