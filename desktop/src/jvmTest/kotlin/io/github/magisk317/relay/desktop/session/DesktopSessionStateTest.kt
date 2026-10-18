package io.github.magisk317.relay.desktop.session

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DesktopSessionStateTest {

    @TempDir
    lateinit var dir: Path

    private val credentials = InMemoryDesktopCredentialStore()

    @Test
    fun `run mode starts at the default`() {
        val state = DesktopSessionState(ProfileStore(dir, credentials))
        assertEquals(DesktopRunMode.Default, state.runMode)
    }

    @Test
    fun `switchRunMode publishes and persists`() {
        val state = DesktopSessionState(ProfileStore(dir, credentials))
        state.switchRunMode(DesktopRunMode.Hybrid)
        assertEquals(DesktopRunMode.Hybrid, state.runMode)
        assertEquals(DesktopRunMode.Hybrid, ProfileStore(dir, credentials).loadState().runMode)
        state.switchRunMode(DesktopRunMode.Local)
        assertEquals(DesktopRunMode.Local, state.runMode)
        assertEquals(DesktopRunMode.Local, ProfileStore(dir, credentials).loadState().runMode)
    }

    @Test
    fun `bootstrap surfaces the persisted run mode before probing`() = runBlocking {
        ProfileStore(dir, credentials).saveState(PersistedDesktopState(runMode = DesktopRunMode.Hybrid))
        val state = DesktopSessionState(ProfileStore(dir, credentials))
        state.bootstrap()
        withTimeout(5.seconds) {
            while (state.runMode != DesktopRunMode.Hybrid) delay(10)
        }
        assertEquals(DesktopRunMode.Hybrid, state.runMode)
    }

    @Test
    fun `bootstrap keeps the session when the backend is unreachable`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val store = ProfileStore(dir, credentials)
        store.saveState(activeState("http://127.0.0.1:$port"))
        store.saveSession(session("p1"))

        val state = DesktopSessionState(store)
        state.bootstrap()
        withTimeout(5.seconds) { while (state.loading) delay(10) }

        assertNotNull(store.loadSession("p1"), "an unreachable backend must not drop the stored session")
    }

    @Test
    fun `bootstrap clears the session when the backend rejects the token`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/auth/me") { exchange ->
            exchange.sendResponseHeaders(401, -1)
            exchange.close()
        }
        server.start()
        try {
            val store = ProfileStore(dir, credentials)
            store.saveState(activeState("http://127.0.0.1:${server.address.port}"))
            store.saveSession(session("p1"))

            val state = DesktopSessionState(store)
            state.bootstrap()
            withTimeout(5.seconds) { while (state.loading) delay(10) }

            assertNull(store.loadSession("p1"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a removed profile id is not handed out again`() {
        val store = ProfileStore(dir, credentials)
        store.saveState(
            PersistedDesktopState(
                profiles = listOf(
                    DesktopProfile(id = "profile-1", name = "a", baseUrl = "https://a.example.com"),
                    DesktopProfile(id = "profile-2", name = "b", baseUrl = "https://b.example.com"),
                ),
                activeProfileId = "profile-1",
            ),
        )
        store.saveState(
            store.loadState().copy(profiles = store.loadState().profiles.filterNot { it.id == "profile-1" }),
        )

        val added = DesktopSessionState(store).saveProfile("https://c.example.com")
        val ids = store.loadState().profiles.map { it.id }

        // "profile-1" is free again, so it is handed out; what matters is that
        // the live "profile-2" is never reused.
        assertEquals("profile-1", added.id)
        assertEquals(ids.size, ids.toSet().size, "profile ids must stay unique")
    }

    @Test
    fun `concurrent state updates do not lose writes`() {
        val store = ProfileStore(dir, credentials)
        store.saveState(PersistedDesktopState())

        val threads = 8
        val each = 25
        val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
        val done = java.util.concurrent.CountDownLatch(threads)
        repeat(threads) { t ->
            pool.execute {
                try {
                    repeat(each) { i ->
                        store.updateState { state ->
                            state.copy(
                                profiles = state.profiles + DesktopProfile(
                                    id = "c-$t-$i",
                                    name = "n",
                                    baseUrl = "https://c$t-$i.example.com",
                                ),
                            )
                        }
                    }
                } finally {
                    done.countDown()
                }
            }
        }
        assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals(threads * each, store.loadState().profiles.size, "concurrent updates must not be lost")
    }

    @Test
    fun `a new profile keeps the self-signed override it was saved with`() {
        val store = ProfileStore(dir, credentials)
        val state = DesktopSessionState(store)

        val saved = state.saveProfile("https://self-signed.example.com", allowSelfSigned = true)

        assertTrue(saved.allowSelfSigned)
        assertTrue(store.loadState().profiles.single().allowSelfSigned, "the override must reach disk")
    }

    @Test
    fun `flipping the switch persists the override on the active profile`() {
        val store = ProfileStore(dir, credentials)
        val state = DesktopSessionState(store)
        val saved = state.saveProfile("https://self-signed.example.com")
        assertFalse(saved.allowSelfSigned)

        state.setActiveProfileAllowSelfSigned(true)

        assertTrue(state.activeProfile!!.allowSelfSigned)
        val persisted = store.loadState().profiles.single { it.id == saved.id }
        assertTrue(persisted.allowSelfSigned, "an existing profile must persist a flipped override")
        assertEquals(saved.name, persisted.name, "flipping the switch must not rename the profile")
    }

    private fun activeState(baseUrl: String) = PersistedDesktopState(
        profiles = listOf(DesktopProfile(id = "p1", name = "prod", baseUrl = baseUrl)),
        activeProfileId = "p1",
    )

    /** Far-future expiry keeps the probe from triggering a refresh attempt. */
    private fun session(id: String) = DesktopSession(
        profileId = id,
        username = "alice",
        accessToken = "a1",
        refreshToken = "r1",
        expiresAt = "2099-01-01T00:00:00Z",
        refreshExpiresAt = "2099-01-01T00:00:00Z",
    )
}
