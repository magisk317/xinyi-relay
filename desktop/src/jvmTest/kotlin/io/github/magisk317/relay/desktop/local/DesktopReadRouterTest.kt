package io.github.magisk317.relay.desktop.local

import io.github.magisk317.relay.desktop.core.model.Device
import io.github.magisk317.relay.desktop.core.store.DesktopClock
import io.github.magisk317.relay.desktop.core.store.DesktopLocalStore
import io.github.magisk317.relay.desktop.data.DesktopDatabaseFactory
import io.github.magisk317.relay.desktop.remote.ConsoleClient
import io.github.magisk317.relay.desktop.session.DesktopRunMode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract of the read router (parity §5): which side answers a console
 * call under each run mode. The mapping fidelity of the mirror side lives in
 * [LocalMirrorClientTest]; what is pinned here is the decision itself — the
 * per-call evaluation (a mode switch takes effect on the next read), the
 * Hybrid carve-out (reads stay remote, the mirror is only the warm cache),
 * the graceful degradation when the mirror never opened, and the no-session
 * case every page's `?: return` relies on.
 */
class DesktopReadRouterTest {

    @Test
    fun `remote mode answers with the http client instance`() {
        val remote = ConsoleClient("http://console.invalid")

        val routed = DesktopReadRouter(
            remoteProvider = { remote },
            storeProvider = { error("remote mode must not touch the mirror") },
            modeProvider = { DesktopRunMode.Remote },
        ).route()

        assertSame(remote, routed)
    }

    @Test
    fun `hybrid mode keeps the http client even with the mirror open`() {
        val remote = ConsoleClient("http://console.invalid")
        val store = seededStore("router-hybrid-test")

        val routed = DesktopReadRouter(
            remoteProvider = { remote },
            storeProvider = { store },
            modeProvider = { DesktopRunMode.Hybrid },
        ).route()

        assertSame(remote, routed, "hybrid reads stay remote; the mirror is the warm cache")
    }

    @Test
    fun `local mode answers from the mirror`() = runBlocking {
        val remote = ConsoleClient("http://console.invalid")
        val store = seededStore("router-local-test")

        val routed = DesktopReadRouter(
            remoteProvider = { remote },
            storeProvider = { store },
            modeProvider = { DesktopRunMode.Local },
        ).route()

        assertTrue(routed is LocalMirrorClient, "local mode must ride the mirror, saw $routed")
        val devices = routed!!.devices().devices
        assertEquals(1, devices.size)
        assertEquals("Pixel", devices.first().displayName)
    }

    @Test
    fun `the decision is re-evaluated per call so a mode switch lands`() {
        val remote = ConsoleClient("http://console.invalid")
        val store = seededStore("router-switch-test")
        var mode = DesktopRunMode.Remote
        val router = DesktopReadRouter(
            remoteProvider = { remote },
            storeProvider = { store },
            modeProvider = { mode },
        )

        assertSame(remote, router.route())
        mode = DesktopRunMode.Local
        assertTrue(router.route() is LocalMirrorClient, "the next read must already ride the mirror")
        mode = DesktopRunMode.Remote
        assertSame(remote, router.route())
    }

    @Test
    fun `local mode degrades to the http client when the mirror never opened`() {
        val remote = ConsoleClient("http://console.invalid")

        val routed = DesktopReadRouter(
            remoteProvider = { remote },
            storeProvider = { null },
            modeProvider = { DesktopRunMode.Local },
        ).route()

        assertSame(remote, routed, "a failed open must not blank every page")
    }

    @Test
    fun `no session means no client at all`() {
        val routed = DesktopReadRouter(
            remoteProvider = { null },
            storeProvider = { null },
            modeProvider = { DesktopRunMode.Local },
        ).route()

        assertNull(routed, "pages' null-check must fire before login")
    }

    private fun seededStore(name: String): DesktopLocalStore =
        DesktopLocalStore(DesktopDatabaseFactory.inMemory(name), DesktopClock()).also { store ->
            runBlocking {
                store.upsertDevices(
                    listOf(
                        Device(
                            id = 7L,
                            deviceName = "pixel",
                            displayName = "Pixel",
                            createdAt = "2026-10-02T00:00:00Z",
                            updatedAt = "2026-10-02T00:00:00Z",
                        ),
                    ),
                )
            }
        }
}
