package io.github.magisk317.relay.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.magisk317.relay.desktop.i18n.DesktopMessages
import io.github.magisk317.relay.desktop.i18n.LocalePreference
import io.github.magisk317.relay.desktop.platform.FileLockInstanceGuard
import io.github.magisk317.relay.desktop.ui.DesktopApp
import io.github.magisk317.relay.desktop.ui.XinyiDesktopTheme
import java.awt.GraphicsEnvironment
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/**
 * Entry point. A cross-process [FileLockInstanceGuard] refuses a second copy
 * before any window opens: an advisory lock is the portable JVM answer (the OS
 * releases it when the holder dies, so a crashed instance never wedges the
 * next launch the way a stale PID file would).
 */
fun main() {
    val instanceGuard = FileLockInstanceGuard(FileLockInstanceGuard.defaultLockFile())
    if (!instanceGuard.acquire()) {
        refuseSecondInstance()
        return
    }
    Runtime.getRuntime().addShutdownHook(Thread { instanceGuard.close() })

    application {
        val windowState = rememberWindowState()
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Xinyi Relay Desktop",
        ) {
            window.minimumSize = java.awt.Dimension(960, 640)
            XinyiDesktopTheme {
                DesktopApp(window = window, windowState = windowState, onQuit = ::exitApplication)
            }
        }
    }
}

/**
 * Second-instance refusal. A modal dialog when a display exists, stderr
 * otherwise (headless / CI), so the reason for the early exit is never
 * silent. Exits with status 1 - a refused launch is a failure, not a
 * no-op the shell might mistake for a crash-loop.
 */
private fun refuseSecondInstance() {
    val locale = LocalePreference.fromSystemLocale()
    val message = DesktopMessages.t(locale, "platform.instanceAlreadyRunning")
    val shown = runCatching {
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null, message, "Xinyi Relay", JOptionPane.WARNING_MESSAGE)
            true
        } else {
            false
        }
    }.getOrDefault(false)
    if (!shown) {
        System.err.println(message)
    }
    exitProcess(1)
}
