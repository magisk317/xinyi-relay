package io.github.magisk317.relay.desktop.platform

/**
 * Tray menu actions. The ids are the Tauri tray's own words
 * (the retired Rust shell), so the menu answers to the
 * same ids and the port stays recognisable next to its reference.
 *
 * [messageKey] is the i18n fragment under `platform.tray.`; it only departs
 * from [id] where the Tauri id is hyphenated (`restart-monitor`) while the
 * message tables use camelCase.
 */
enum class TrayAction(val id: String, val messageKey: String) {
    SHOW("show", "show"),
    OVERVIEW("overview", "overview"),
    DEVICES("devices", "devices"),
    RECORDS("records", "records"),
    RESTART_MONITOR("restart-monitor", "restartMonitor"),
    QUIT("quit", "quit"),
}

/** One tray menu row. [separatorBefore] draws the AWT separator above the row. */
data class TrayMenuItem(
    val action: TrayAction,
    val label: String,
    val separatorBefore: Boolean = false,
)

/**
 * The tray menu in Tauri order: show, three page jumps, reconnect monitor, a
 * separator, then quit. [label] resolves the localized text for an action, so
 * the menu model stays free of the i18n tables.
 */
object TrayMenu {
    fun items(label: (TrayAction) -> String): List<TrayMenuItem> = listOf(
        TrayMenuItem(TrayAction.SHOW, label(TrayAction.SHOW)),
        TrayMenuItem(TrayAction.OVERVIEW, label(TrayAction.OVERVIEW)),
        TrayMenuItem(TrayAction.DEVICES, label(TrayAction.DEVICES)),
        TrayMenuItem(TrayAction.RECORDS, label(TrayAction.RECORDS)),
        TrayMenuItem(TrayAction.RESTART_MONITOR, label(TrayAction.RESTART_MONITOR)),
        TrayMenuItem(TrayAction.QUIT, label(TrayAction.QUIT), separatorBefore = true),
    )
}

/**
 * System tray presence: icon, tooltip, popup menu and left-click wake.
 *
 * The seam exists for the same headless reason as [DesktopNotifier]:
 * `java.awt.SystemTray` is missing on plenty of Linux setups (no notification
 * area daemon) and in CI, and callers must be able to tell "installed" from
 * "no tray to install into" apart. A left click on the icon and the show row
 * both wake the window through [onWake]; every other row arrives as its
 * [TrayAction] through [onSelect].
 */
interface DesktopTray : AutoCloseable {

    /** True once a system tray accepted the icon. */
    val installed: Boolean

    /**
     * Installs the tray icon carrying [items].
     *
     * @return true when a system tray accepted the icon.
     */
    fun install(items: List<TrayMenuItem>, onSelect: (TrayAction) -> Unit, onWake: () -> Unit): Boolean

    /** Re-labels the menu of an installed icon (locale switch); no-op otherwise. */
    fun updateItems(items: List<TrayMenuItem>)

    /** Replaces the icon tooltip; no-op when nothing is installed. */
    fun setTooltip(text: String)
}
