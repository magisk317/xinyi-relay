package io.github.magisk317.relay.desktop.session

import com.sun.net.httpserver.HttpServer
import io.github.magisk317.relay.desktop.remote.ConsoleClient
import java.net.InetSocketAddress
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SessionManagerTest {

    @TempDir
    lateinit var dir: Path
    private lateinit var server: HttpServer
    private var refreshCalls = 0
    private var logoutCalls = 0
    private lateinit var credentials: InMemoryDesktopCredentialStore

    @BeforeEach
    fun setUp() {
        refreshCalls = 0
        logoutCalls = 0
        credentials = InMemoryDesktopCredentialStore()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/auth/desktop/refresh") { exchange ->
            refreshCalls++
            val body = """{"authenticated":true,"username":"alice","accessToken":"a-new","refreshToken":"r-new","expiresAt":"${Instant.now().plus(1, ChronoUnit.HOURS)}","refreshExpiresAt":"${Instant.now().plus(30, ChronoUnit.DAYS)}"}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/api/v1/auth/desktop/logout") { exchange ->
            logoutCalls++
            val body = """{"ok":true}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    private fun profile() = DesktopProfile(id = "p1", name = "prod", baseUrl = "http://127.0.0.1:${server.address.port}")

    private fun store() = ProfileStore(dir, credentials)

    private fun seedSession(expiresAt: Instant) {
        store().saveSession(
            DesktopSession(
                profileId = "p1",
                username = "alice",
                accessToken = "a-old",
                refreshToken = "r-old",
                expiresAt = expiresAt.toString(),
                refreshExpiresAt = Instant.now().plus(30, ChronoUnit.DAYS).toString(),
            ),
        )
    }

    @Test
    fun `client is seeded from persisted session`() {
        seedSession(Instant.now().plus(1, ChronoUnit.HOURS))
        val client = SessionManager(store()).clientFor(profile())
        assertEquals("a-old", client.accessToken)
    }

    @Test
    fun `expired access token triggers refresh`() = runBlocking {
        seedSession(Instant.now().minus(1, ChronoUnit.MINUTES))
        val manager = SessionManager(store())
        val client = manager.clientFor(profile())
        assertTrue(manager.refreshIfExpiring(profile(), client))
        assertEquals(1, refreshCalls)
        assertEquals("a-new", client.accessToken)
        assertEquals("a-new", store().loadSession("p1")?.accessToken)
    }

    @Test
    fun `fresh access token skips refresh`() = runBlocking {
        seedSession(Instant.now().plus(1, ChronoUnit.HOURS))
        val manager = SessionManager(store())
        val client = manager.clientFor(profile())
        assertFalse(manager.refreshIfExpiring(profile(), client))
        assertEquals(0, refreshCalls)
    }

    @Test
    fun `logout clears persisted session`() = runBlocking {
        seedSession(Instant.now().plus(1, ChronoUnit.HOURS))
        val manager = SessionManager(store())
        val client = manager.clientFor(profile())
        manager.logout(profile(), client)
        assertEquals(1, logoutCalls)
        assertNull(store().loadSession("p1"))
        assertNull(client.accessToken)
    }
}
