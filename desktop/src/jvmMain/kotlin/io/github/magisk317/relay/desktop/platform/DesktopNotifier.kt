package io.github.magisk317.relay.desktop.platform

/**
 * Desktop banner delivery.
 *
 * The webUI answers "did anything happen" through the page; the desktop shell
 * has no page to look at while minimised, so anything the user should notice
 * while the window is in the background goes through here. The seam exists for
 * the headless tray problem: `java.awt.SystemTray` is unavailable on plenty of
 * Linux setups (no notification area daemon) and in CI, and callers must be
 * able to tell "shown" from "nobody was there to show it" apart.
 */
interface DesktopNotifier {

    /** Severity only shapes the tray icon decoration; delivery is best effort. */
    enum class Level { INFO, WARNING }

    /**
     * Shows [title]/[body] as a desktop notification.
     *
     * @return true when a system tray accepted the message, false when the
     *   platform had no tray to deliver it. Callers treat false as a soft
     *   failure: the in-app feed still carries the event.
     */
    fun notify(title: String, body: String, level: Level = Level.INFO): Boolean
}
