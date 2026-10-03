package io.github.magisk317.relay.desktop.local

import io.github.magisk317.relay.contract.json.RelayJson
import io.github.magisk317.relay.contract.remote.AgentRegisterResponse
import io.github.magisk317.relay.desktop.core.store.DesktopClock
import io.github.magisk317.relay.desktop.core.store.DesktopLocalStore
import io.github.magisk317.relay.desktop.data.DesktopDatabase
import io.github.magisk317.relay.desktop.data.DesktopDatabaseFactory
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DesktopLocalServerTest {

    private lateinit var database: DesktopDatabase
    private lateinit var store: DesktopLocalStore
    private lateinit var server: DesktopLocalServer

    @BeforeEach
    fun setUp() {
        database = DesktopDatabaseFactory.inMemory("local-server-test-${System.nanoTime()}")
        store = DesktopLocalStore(database, DesktopClock())
        server = DesktopLocalServer(store)
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
        database.close()
    }

    @Test
    fun `health system info and unknown paths expose the expected surface`() {
        assertEquals(200, request("/healthz").status)
        val info = request("/api/v1/system/info")
        assertEquals(200, info.status)
        assertTrue(info.body.contains("xinyi-relay-desktop"))
        assertEquals(404, request("/missing").status)
        assertEquals(405, request("/healthz", method = "POST", body = "{}").status)
    }

    @Test
    fun `registration is single use and authenticated agent routes work`() {
        val bindCode = runBlocking { store.createBindCode().code }
        val registered = request(
            "/api/v1/agent/register",
            method = "POST",
            body = """
                {
                  "bindCode":"$bindCode",
                  "deviceName":"Phone",
                  "deviceModel":"Pixel",
                  "platform":"android",
                  "appVersion":"1.0"
                }
            """.trimIndent(),
        )
        assertEquals(201, registered.status)
        val response = RelayJson.decode(AgentRegisterResponse.serializer(), registered.body)
        assertTrue(response.deviceToken.isNotBlank())

        assertEquals(
            401,
            request(
                "/api/v1/agent/heartbeat",
                method = "POST",
                body = "{}",
            ).status,
        )
        assertEquals(
            401,
            request(
                "/api/v1/agent/register",
                method = "POST",
                body = """{"bindCode":"$bindCode","deviceName":"again"}""",
            ).status,
        )

        val auth = mapOf("Authorization" to "Bearer ${response.deviceToken}")
        assertEquals(
            200,
            request(
                "/api/v1/agent/heartbeat",
                method = "POST",
                body = """{"appVersion":"1.1","localAddresses":[],"capabilities":{}}""",
                headers = auth,
            ).status,
        )
        assertEquals(
            200,
            request(
                "/api/v1/agent/config/mirror",
                method = "POST",
                body = """{"localRevision":1,"mirrorContent":{"senders":[]}}""",
                headers = auth,
            ).status,
        )
        val pulled = request(
            "/api/v1/agent/config/commands:pull",
            method = "POST",
            body = """{"localRevision":0}""",
            headers = auth,
        )
        assertEquals(200, pulled.status)
        assertTrue(pulled.body.contains("\"revision\":1"))

        val uploaded = request(
            "/api/v1/agent/records:batch",
            method = "POST",
            body = """
                {"records":[{"eventId":"evt-1","recordType":"sms","sender":"a",
                "body":"hello","smsCode":"1234","packageName":"pkg","msgType":1,
                "callType":0,"occurredAt":"2026-01-01T00:00:00Z","metadata":{}}]}
            """.trimIndent(),
            headers = auth,
        )
        assertEquals(200, uploaded.status)
        assertTrue(uploaded.body.contains("\"inserted\":1"))
    }

    @Test
    fun `limits and store errors map to stable HTTP statuses`() {
        val bindCode = runBlocking { store.createBindCode().code }
        val oversizedName = "n".repeat(513)
        val registered = request(
            "/api/v1/agent/register",
            method = "POST",
            body = """{"bindCode":"$bindCode","deviceName":"$oversizedName"}""",
        )
        assertEquals(201, registered.status)
        val device = runBlocking { store.listDevices().single() }
        assertEquals(512, device.deviceName.length)
        val token = RelayJson.decode(AgentRegisterResponse.serializer(), registered.body).deviceToken
        val auth = mapOf("Authorization" to "Bearer $token")

        val conflict = request(
            "/api/v1/agent/config/commands:ack",
            method = "POST",
            body = """{"commandId":999,"status":"applied","appliedRevision":1,"mirrorContent":{}}""",
            headers = auth,
        )
        assertEquals(409, conflict.status)

        val internal = request(
            "/api/v1/agent/config/commands:ack",
            method = "POST",
            body = """{"commandId":999,"status":"unsupported","appliedRevision":1,"mirrorContent":{}}""",
            headers = auth,
        )
        assertEquals(500, internal.status)

        val tooMany = (0..200).joinToString(",") {
            "{\"eventId\":\"$it\",\"recordType\":\"sms\",\"sender\":\"\",\"body\":\"\",\"smsCode\":\"\",\"packageName\":\"\",\"msgType\":0,\"callType\":0,\"occurredAt\":\"\",\"metadata\":{}}"
        }
        assertEquals(
            400,
            request(
                "/api/v1/agent/records:batch",
                method = "POST",
                body = "{\"records\":[$tooMany]}",
                headers = auth,
            ).status,
        )

        val oversizedBody = "x".repeat(8 * 1024 * 1024 + 1)
        assertEquals(
            413,
            request(
                "/api/v1/agent/records:batch",
                method = "POST",
                body = oversizedBody,
                headers = auth,
            ).status,
        )
    }

    @Test
    fun `start is idempotent and stop releases the listener`() {
        val first = server.address
        assertNotNull(first)
        assertEquals(first, server.start())
        server.stop()
        assertNull(server.address)
        val failed = runCatching { request("/healthz", timeoutMillis = 500) }
        assertTrue(failed.isFailure || failed.getOrNull()?.status != 200)
        assertFalse(server.address != null)
    }

    private fun request(
        path: String,
        method: String = "GET",
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        timeoutMillis: Int = 5_000,
    ): HttpResponse {
        val address = requireNotNull(server.address)
        val connection = URI("http://${address.hostString}:${address.port}$path").toURL()
            .openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { output -> output.write(body.toByteArray()) }
        }
        val status = connection.responseCode
        val stream = if (status >= 400) connection.errorStream else connection.inputStream
        val responseBody = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        connection.disconnect()
        return HttpResponse(status, responseBody)
    }

    private data class HttpResponse(val status: Int, val body: String)
}
