package io.github.magisk317.relay.desktop.platform

import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * [DesktopTray] over `java.awt.SystemTray`.
 *
 * One icon per process: [trayIcon] exposes the live [TrayIcon] so the
 * process notifier ([AwtNotifier]) can display balloon messages on this very
 * icon instead of registering a second tray entry. A tray icon cannot be
 * registered without an image, and the module ships no icon binary until a
 * real asset lands, so the generated [PlaceholderImages.tray] square fills in.
 *
 * The icon is added once per instance and removed on [close]; leaking tray
 * registrations keeps dead icons alive until the JVM exits, which shows up as
 * a ghost entry after the app closes. Menu actions and the left-click wake
 * arrive on the AWT event thread.
 */
class AwtTray(
    private val image: Image = PlaceholderImages.tray(),
    private val tray: SystemTray? = if (SystemTray.isSupported()) SystemTray.getSystemTray() else null,
    private val tooltip: String = DEFAULT_TOOLTIP,
) : DesktopTray, AutoCloseable {

    private var icon: TrayIcon? = null
    private var items: List<TrayMenuItem> = emptyList()
    private var onSelect: (TrayAction) -> Unit = {}
    private var onWake: () -> Unit = {}

    override var installed: Boolean = false
        private set

    /** The live tray icon, or null before install / without a system tray. */
    val trayIcon: TrayIcon? get() = icon

    override fun install(items: List<TrayMenuItem>, onSelect: (TrayAction) -> Unit, onWake: () -> Unit): Boolean {
        val tray = tray ?: return false
        this.items = items
        this.onSelect = onSelect
        this.onWake = onWake
        val trayIcon = TrayIcon(image, tooltip, buildPopup(items)).apply {
            isImageAutoSize = true
            addActionListener { onWake() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.button == MouseEvent.BUTTON1) onWake()
                }
                override fun mouseReleased(event: MouseEvent) {
                    if (event.button == MouseEvent.BUTTON1) onWake()
                }
            })
        }
        installed = runCatching { tray.add(trayIcon) }.isSuccess
        icon = trayIcon
        return installed
    }

    override fun updateItems(items: List<TrayMenuItem>) {
        this.items = items
        val trayIcon = icon ?: return
        trayIcon.popupMenu = buildPopup(items)
    }

    override fun setTooltip(text: String) {
        icon?.toolTip = text
    }

    override fun close() {
        icon?.let { existing ->
            runCatching { tray?.remove(existing) }
            icon = null
        }
        installed = false
    }

    private fun buildPopup(items: List<TrayMenuItem>): PopupMenu = PopupMenu().apply {
        items.forEach { item ->
            if (item.separatorBefore) addSeparator()
            add(MenuItem(item.label).apply {
                addActionListener { onSelect(item.action) }
            })
        }
    }

    private companion object {
        const val DEFAULT_TOOLTIP = "Xinyi Relay"
    }
}
