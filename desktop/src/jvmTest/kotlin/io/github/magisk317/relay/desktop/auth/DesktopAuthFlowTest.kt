package io.github.magisk317.relay.desktop.auth

import com.sun.net.httpserver.HttpServer
import io.github.magisk317.relay.desktop.remote.ConsoleClient
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesktopAuthFlowTest {

    private fun hitCallback(url: String): String {
        val req = HttpRequest.newBuilder(URI.create(url)).GET().build()
        return HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString()).body()
    }

    @Test
    fun `callback delivers code and state`() = runBlocking {
        val flow = DesktopAuthFlow()
        flow.start()
        hitCallback("${flow.callbackUrl}?code=abc123&state=${flow.state}")
        val payload = flow.awaitCallback(10_000)
        assertEquals("abc123", payload.code)
        assertEquals(flow.state, payload.state)
        flow.close()
    }

    @Test
    fun `completeLogin exchanges code against backend`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var exchangeBody = ""
        server.createContext("/api/v1/auth/desktop/exchange") { exchange ->
            exchangeBody = exchange.requestBody.readBytes().decodeToString()
            val body = """{"authenticated":true,"username":"alice","accessToken":"a1","refreshToken":"r1","expiresAt":"2026-10-02T00:00:00Z","refreshExpiresAt":"2026-11-02T00:00:00Z"}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val client = ConsoleClient(base)
            val flow = DesktopAuthFlow()
            flow.start()
            val authUrl = flow.authUrl(base)
            assertTrue(authUrl.startsWith("$base/api/v1/auth/desktop/start?redirect_uri="), authUrl)
            assertTrue(authUrl.contains("state=${flow.state}"), authUrl)
            hitCallback("${flow.callbackUrl}?code=xyz&state=${flow.state}")
            val session = flow.completeLogin(client, 10_000)
            assertEquals("a1", session.accessToken)
            assertTrue(exchangeBody.contains("\"code\":\"xyz\""), exchangeBody)
            flow.close()
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `state mismatch is rejected`() {
        val flow = DesktopAuthFlow()
        flow.start()
        hitCallback("${flow.callbackUrl}?code=abc&state=WRONG")
        val client = ConsoleClient("http://127.0.0.1:1")
        val ex = assertThrows(IllegalStateException::class.java) {
            runBlocking { flow.completeLogin(client, 10_000) }
        }
        assertTrue(ex.message!!.contains("state mismatch"))
        flow.close()
    }
}
