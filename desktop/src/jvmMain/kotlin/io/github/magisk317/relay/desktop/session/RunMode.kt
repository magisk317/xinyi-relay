package io.github.magisk317.relay.desktop.session

import kotlinx.serialization.Serializable

/**
 * Which backend the app talks to, mirroring the Rust desktop's `RunMode`
 * (`src-tauri/src/main.rs`, serde camelCase, default `Remote`):
 *
 * - [Remote] — the mode the shell has always run in: every page reads through
 *   ConsoleClient against the profile's backend and the local SQLite mirror
 *   stays closed. The default, so a fresh install (and a `profiles.json`
 *   predating the field) behaves exactly as before.
 * - [Local] — reads are served from the local mirror, writes queue against it,
 *   and the agent-facing local server answers heartbeats/reports.
 * - [Hybrid] — the remote client stays primary while the local mirror keeps
 *   pulling in the background and the local server stays up.
 *
 * The value is persisted inside `profiles.json`
 * (`PersistedDesktopState.runMode`). This slice implements the model, the
 * persistence and the mirror-assembly gate; the mode-switcher UI, Local read
 * routing and the local server itself are the remaining gaps tracked in
 * `docs/DESKTOP_PARITY.md` §5.
 */
@Serializable
enum class DesktopRunMode {
    Local,
    Remote,
    Hybrid,
    ;

    /**
     * Whether the local SQLite mirror participates under this mode. It is the
     * read store of [Local] and the warm cache of [Hybrid]; [Remote] leaves it
     * closed, the way the Rust default does.
     */
    val usesLocalMirror: Boolean get() = this != Remote

    /** Message key of this mode's display label (settings selector row). */
    val labelKey: String
        get() = when (this) {
            Local -> "settings.runMode.local"
            Remote -> "settings.runMode.remote"
            Hybrid -> "settings.runMode.hybrid"
        }

    /** Message key of this mode's one-line effect description. */
    val descriptionKey: String
        get() = when (this) {
            Local -> "settings.runMode.localDesc"
            Remote -> "settings.runMode.remoteDesc"
            Hybrid -> "settings.runMode.hybridDesc"
        }

    companion object {
        /** Fresh install, and state files predating the persisted field. */
        val Default: DesktopRunMode = Remote
    }
}
