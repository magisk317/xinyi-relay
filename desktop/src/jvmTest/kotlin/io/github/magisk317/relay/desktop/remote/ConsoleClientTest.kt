package io.github.magisk317.relay.desktop.remote

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConsoleClientTest {

    private lateinit var server: HttpServer
    private val responses = mutableMapOf<String, Pair<Int, String>>()
    private var lastAuth: String? = null
    private var lastMethod: String = ""
    private var lastPathWithQuery: String = ""
    private var lastBody: String = ""

    @BeforeEach
    fun setUp() {
        responses.clear()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            lastAuth = exchange.requestHeaders.getFirst("Authorization")
            lastMethod = exchange.requestMethod
            lastPathWithQuery = exchange.requestURI.path +
                (exchange.requestURI.rawQuery?.let { "?$it" } ?: "")
            lastBody = exchange.requestBody.readBytes().decodeToString()
            val (status, body) = responses[exchange.requestURI.path] ?: (404 to """{"error":"not_found"}""")
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    private fun baseUrl(): String = "http://127.0.0.1:${server.address.port}"

    @Test
    fun `system info is fetched without auth`() = runBlocking {
        responses["/api/v1/system/info"] = 200 to """{"service":"relay","appEnv":"prod","userCount":3}"""
        val info = ConsoleClient(baseUrl()).systemInfo()
        assertEquals("relay", info.service)
        assertEquals(3L, info.userCount)
        assertNull(lastAuth)
    }

    @Test
    fun `bearer header is attached when token set`() = runBlocking {
        responses["/api/v1/auth/me"] = 200 to """{"authenticated":true,"username":"alice"}"""
        val client = ConsoleClient(baseUrl())
        client.accessToken = "tok-1"
        val me = client.me()
        assertEquals("alice", me.username)
        assertEquals("Bearer tok-1", lastAuth)
    }

    @Test
    fun `exchange posts code and decodes session`() = runBlocking {
        responses["/api/v1/auth/desktop/exchange"] = 200 to
            """{"authenticated":true,"username":"alice","accessToken":"a1","refreshToken":"r1","expiresAt":"2026-10-02T00:00:00Z","refreshExpiresAt":"2026-11-02T00:00:00Z"}"""
        val session = ConsoleClient(baseUrl()).desktopExchange("code-9")
        assertEquals("a1", session.accessToken)
        assertEquals("POST", lastMethod)
        assertTrue(lastBody.contains("\"code\":\"code-9\""), lastBody)
        assertNull(lastAuth)
    }

    @Test
    fun `devices decode with json object fields`() = runBlocking {
        responses["/api/v1/devices"] = 200 to """{"devices":[{"id":7,"userId":1,"deviceName":"Pixel","displayName":"Pixel 8","enabled":true,"localAddresses":{},"capabilities":{"sms":true},"createdAt":"2026-09-01T00:00:00Z","updatedAt":"2026-09-01T00:00:00Z"}]}"""
        val devices = ConsoleClient(baseUrl()).devices()
        assertEquals(1, devices.devices.size)
        assertEquals(7L, devices.devices[0].id)
    }

    @Test
    fun `device config 404 becomes null`() = runBlocking {
        responses["/api/v1/devices/9/config"] = 404 to """{"error":"no_config"}"""
        assertNull(ConsoleClient(baseUrl()).deviceConfig(9))
    }

    @Test
    fun `server error surfaces as api exception`() {
        responses["/api/v1/devices"] = 500 to """{"error":"boom"}"""
        val client = ConsoleClient(baseUrl())
        val ex = assertThrows(ConsoleApiException::class.java) { runBlocking { client.devices() } }
        assertEquals(500, ex.status)
        assertTrue(ex.message!!.contains("boom"))
    }

    @Test
    fun `records query carries limit and device id`() = runBlocking {
        responses["/api/v1/records"] = 200 to """{"records":[],"limit":10,"offset":0}"""
        ConsoleClient(baseUrl()).records(limit = 10, deviceId = 42L)
        assertTrue(lastPathWithQuery.contains("limit=10"), lastPathWithQuery)
        assertTrue(lastPathWithQuery.contains("device_id=42"), lastPathWithQuery)
    }

    @Test
    fun `device config audit logs decode and carry query params`() = runBlocking {
        responses["/api/v1/devices/9/config/audit"] = 200 to """{"logs":[{"id":5,"deviceId":9,"commandId":4,"revision":12,"eventType":"config.applied","actorType":"user","actorId":3,"summary":"push config","createdAt":"2026-10-01T00:00:00Z"}],"limit":30,"offset":0}"""
        val logs = ConsoleClient(baseUrl()).deviceConfigAuditLogs(9L, 30, 0)
        assertEquals(1, logs.logs.size)
        assertEquals("config.applied", logs.logs[0].eventType)
        assertEquals(12L, logs.logs[0].revision)
        assertEquals(3L, logs.logs[0].actorId)
        assertTrue(lastPathWithQuery.contains("limit=30"), lastPathWithQuery)
        assertTrue(lastPathWithQuery.contains("offset=0"), lastPathWithQuery)
    }
}
