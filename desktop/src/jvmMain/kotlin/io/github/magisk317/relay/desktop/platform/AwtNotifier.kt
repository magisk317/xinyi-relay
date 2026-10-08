package io.github.magisk317.relay.desktop.platform

import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * [DesktopNotifier] over `java.awt.SystemTray`.
 *
 * A tray icon cannot be registered without an image, and this module ships no
 * icon binary (see `desktop/src/jvmMain/resources`, which is deliberately
 * empty until an actual asset lands). Rather than block the whole capability
 * on art, a 16x16 flat square is generated at runtime: it satisfies
 * `TrayIcon`'s non-null image contract and is unremarkable in any real
 * notification area. When a real tray icon ships, replace [placeholderImage].
 *
 * [sharedIcon] is how the process keeps exactly one tray entry: when an
 * [AwtTray] is installed, balloons are displayed on its icon instead of
 * registering a second one. Without it this class still registers (and owns)
 * its own icon, which is what the headless-less standalone path and the tests
 * exercise.
 *
 * The icon is added once per instance and removed on [close]; leaking tray
 * registrations keeps dead icons alive until the JVM exits, which shows up as
 * a ghost entry after the app closes.
 *
 * No logger on purpose: the module has no logging dependency, and the
 * [DesktopNotifier.notify] return value already tells callers whether a tray
 * accepted the message.
 */
class AwtNotifier(
    private val placeholderImage: Image = PlaceholderImages.tray(),
    private val tray: SystemTray? = if (SystemTray.isSupported()) SystemTray.getSystemTray() else null,
    private val sharedIcon: () -> TrayIcon? = { null },
) : DesktopNotifier, AutoCloseable {

    private var icon: TrayIcon? = null

    override fun notify(title: String, body: String, level: DesktopNotifier.Level): Boolean {
        val tray = tray ?: return false
        val trayIcon = sharedIcon() ?: icon ?: TrayIcon(placeholderImage, TOOLTIP).also { created ->
            created.isImageAutoSize = true
            runCatching { tray.add(created) }
            icon = created
        }
        return runCatching {
            trayIcon.displayMessage(title, body, awtMessageType(level))
            true
        }.getOrDefault(false)
    }

    override fun close() {
        icon?.let { existing ->
            runCatching { tray?.remove(existing) }
            icon = null
        }
    }

    private fun awtMessageType(level: DesktopNotifier.Level): TrayIcon.MessageType = when (level) {
        DesktopNotifier.Level.INFO -> TrayIcon.MessageType.INFO
        DesktopNotifier.Level.WARNING -> TrayIcon.MessageType.WARNING
    }

    private companion object {
        const val TOOLTIP = "Xinyi Relay"
    }
}
