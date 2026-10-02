package io.github.magisk317.relay.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.session.DesktopSessionState

/**
 * Desktop port of the webUI login page. Authentication is the browser
 * handoff (the desktop equivalent of submitting the webUI form): the start
 * URL is opened in the system browser, the backend signs the user in there
 * and redirects back to the loopback callback captured by DesktopAuthFlow.
 *
 * The webUI talks to its same-origin backend; the desktop needs an explicit
 * backend URL, so an unconfigured install shows the profile field first.
 */
@Composable
fun LoginScreen(
    session: DesktopSessionState,
    locale: DesktopLocale,
    modifier: Modifier = Modifier,
) {
    var baseUrl by remember { mutableStateOf(session.activeProfile?.baseUrl ?: "") }
    var error by remember { mutableStateOf("") }
    val hasProfile = session.activeProfile != null

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(enabled = true),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "XC",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                Text(
                    text = DesktopMessages.t(locale, "login.brand").uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = DesktopMessages.t(locale, "login.title"),
                    style = MaterialTheme.typography.headlineLarge,
                )

                StatusPill(
                    loading = session.loading || session.loginBusy,
                    connected = session.connected,
                    connectingText = DesktopMessages.t(locale, "login.status.connecting"),
                    readyText = DesktopMessages.t(locale, "login.status.ready"),
                    failedText = DesktopMessages.t(locale, "login.status.failed"),
                )

                if (!hasProfile) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(DesktopLocalStrings.t(locale, "backendUrl")) },
                        placeholder = { Text("http://127.0.0.1:8080") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                Button(
                    onClick = {
                        error = ""
                        if (!hasProfile && baseUrl.isBlank()) {
                            error = DesktopLocalStrings.t(locale, "backendUrlRequired")
                            return@Button
                        }
                        if (!hasProfile) {
                            session.saveProfile(baseUrl)
                        }
                        session.signIn { result ->
                            result.onFailure { error = it.message ?: DesktopMessages.t(locale, "login.failed") }
                        }
                    },
                    enabled = !session.loginBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    if (session.loginBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = DesktopMessages.t(locale, "login.submit"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Text(
                    text = DesktopLocalStrings.t(locale, "browserHandoffHint"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (!session.adminInitialized) {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Text(
                            text = DesktopMessages.t(locale, "login.bootstrapHint"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    loading: Boolean,
    connected: Boolean,
    connectingText: String,
    readyText: String,
    failedText: String,
) {
    val container = when {
        loading -> MaterialTheme.colorScheme.secondaryContainer
        connected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when {
        loading -> MaterialTheme.colorScheme.onSecondaryContainer
        connected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text = when {
                loading -> connectingText
                connected -> readyText
                else -> failedText
            },
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
