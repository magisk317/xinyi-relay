package io.github.magisk317.relay.net

import java.net.Proxy
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Protocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HttpClientConfigTest {
    @Test
    fun `basic auth is removed from URL and returned as a header`() {
        val result = parseBasicAuthUrl("https://alice:secret@example.com:8443/push?id=1")

        assertEquals("https://example.com:8443/push?id=1", result.url)
        assertEquals(Credentials.basic("alice", "secret"), result.authorization)
    }

    @Test
    fun `URL without valid basic auth is unchanged`() {
        val plain = parseBasicAuthUrl("https://example.com/push")
        val invalid = parseBasicAuthUrl("not a url")

        assertEquals(BasicAuthUrl("https://example.com/push", null), plain)
        assertEquals(BasicAuthUrl("not a url", null), invalid)
    }

    @Test
    fun `valid proxy and credentials are installed`() {
        val client = OkHttpClient.Builder().applyProxy(
            ProxyConfig(
                type = Proxy.Type.HTTP,
                host = "127.0.0.1",
                port = "7890",
                authenticate = true,
                username = "proxy-user",
                password = "proxy-pass",
            ),
        ).build()

        assertEquals(Proxy.Type.HTTP, client.proxy?.type())
        val request = Request.Builder().url("https://example.com").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(407)
            .message("Proxy Authentication Required")
            .build()
        val authenticated = client.proxyAuthenticator.authenticate(null, response)
        assertEquals(
            Credentials.basic("proxy-user", "proxy-pass"),
            authenticated?.header("Proxy-Authorization"),
        )
    }

    @Test
    fun `direct and invalid proxies are ignored`() {
        val direct = OkHttpClient.Builder().applyProxy(
            ProxyConfig(Proxy.Type.DIRECT, "127.0.0.1", "7890"),
        ).build()
        val invalidPort = OkHttpClient.Builder().applyProxy(
            ProxyConfig(Proxy.Type.SOCKS, "127.0.0.1", "0"),
        ).build()

        assertNull(direct.proxy)
        assertNull(invalidPort.proxy)
    }
}
