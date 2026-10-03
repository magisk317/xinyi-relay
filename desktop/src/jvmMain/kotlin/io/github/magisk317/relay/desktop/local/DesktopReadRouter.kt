package io.github.magisk317.relay.desktop.local

import io.github.magisk317.relay.desktop.core.store.DesktopLocalStore
import io.github.magisk317.relay.desktop.remote.ConsoleClient
import io.github.magisk317.relay.desktop.remote.ConsoleDataClient
import io.github.magisk317.relay.desktop.session.DesktopRunMode

/**
 * Decides which [ConsoleDataClient] answers a console call: the HTTP remote
 * client, or the local mirror under the Local run mode.
 *
 * This is the KMP seat of the Rust shell's per-command routing
 * (`main.rs`: `if mode == RunMode::Local { ...local store... }`), evaluated
 * per call so a mode switch in Settings takes effect on the next read
 * without rebuilding the shell:
 *
 * - **Remote** — every call rides the HTTP client, unchanged behaviour.
 * - **Local** — calls ride [LocalMirrorClient] over the mirror the sync
 *   controller owns. The mirror is opened by the composition root exactly
 *   when the mode uses it, so the store is present in the normal flow; if it
 *   ever is not (an open failure), the router degrades to the remote client
 *   instead of blanking every page — the same graceful degradation a failed
 *   sync round already applies to the footer status.
 * - **Hybrid** — reads stay remote on purpose: the mirror is only the warm
 *   cache the 5-minute pull keeps fresh, matching the Rust mode where the
 *   remote client stays primary.
 *
 * Providers are read live on every [route] call, so profile switches (new
 * remote client) and mirror re-opens are picked up without re-wiring.
 */
class DesktopReadRouter(
    private val remoteProvider: () -> ConsoleClient?,
    private val storeProvider: () -> DesktopLocalStore?,
    private val modeProvider: () -> DesktopRunMode,
) {

    fun route(): ConsoleDataClient? {
        val remote = remoteProvider() ?: return null
        if (modeProvider() != DesktopRunMode.Local) return remote
        val store = storeProvider() ?: return remote
        return LocalMirrorClient(store)
    }
}
