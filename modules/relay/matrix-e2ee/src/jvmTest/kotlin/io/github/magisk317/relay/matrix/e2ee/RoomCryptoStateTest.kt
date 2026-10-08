package io.github.magisk317.relay.matrix.e2ee

import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking

class RoomCryptoStateTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        RoomCryptoState.clearCache()
    }

    @AfterEach
    fun tearDown() {
        server.close()
        RoomCryptoState.clearCache()
    }

    @Test
    fun `returns true when HTTP 200 with supported algorithm`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"algorithm":"m.megolm.v1.aes-sha2","rotation_period_ms":604800000}""")
                .build()
        )

        val result = RoomCryptoState.isRoomEncrypted(
            homeserver = server.url("/").toString(),
            accessToken = "test-token",
            roomId = "!room:example.com",
            client = client
        )

        assertTrue(result)
    }

    @Test
    fun `returns false when HTTP 404`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("""{"errcode":"M_NOT_FOUND"}""")
                .build()
        )

        val result = RoomCryptoState.isRoomEncrypted(
            homeserver = server.url("/").toString(),
            accessToken = "test-token",
            roomId = "!room:example.com",
            client = client
        )

        assertFalse(result)
    }

    @Test
    fun `authentication failures are propagated and never cached`() = runBlocking {
        listOf(401, 403).forEach { statusCode ->
            val roomId = "!auth-$statusCode:example.com"
            server.enqueue(
                MockResponse.Builder()
                    .code(statusCode)
                    .body("""{"errcode":"M_FORBIDDEN"}""")
                    .build()
            )

            val error = runCatching {
                RoomCryptoState.isRoomEncrypted(
                    homeserver = server.url("/").toString(),
                    accessToken = "expired-token",
                    roomId = roomId,
                    client = client,
                )
            }.exceptionOrNull()

            assertTrue(error is RoomCryptoState.AuthenticationException)
            assertFalse(RoomCryptoState.cache.containsKey(roomId))

            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"algorithm":"m.megolm.v1.aes-sha2"}""")
                    .build()
            )
            assertTrue(
                RoomCryptoState.isRoomEncrypted(
                    homeserver = server.url("/").toString(),
                    accessToken = "fresh-token",
                    roomId = roomId,
                    client = client,
                )
            )
        }

        assertEquals(4, server.requestCount)
    }

    @Test
    fun `returns false when unsupported algorithm`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"algorithm":"m.olm.v1.curve25519-aes-sha2"}""")
                .build()
        )

        val result = RoomCryptoState.isRoomEncrypted(
            homeserver = server.url("/").toString(),
            accessToken = "test-token",
            roomId = "!room:example.com",
            client = client
        )

        assertFalse(result)
    }

    @Test
    fun `returns false when response body is not valid JSON`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("not json at all")
                .build()
        )

        val result = RoomCryptoState.isRoomEncrypted(
            homeserver = server.url("/").toString(),
            accessToken = "test-token",
            roomId = "!room:example.com",
            client = client
        )

        assertFalse(result)
    }

    @Test
    fun `returns false when algorithm field is missing`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"rotation_period_ms":604800000}""")
                .build()
        )

        val result = RoomCryptoState.isRoomEncrypted(
            homeserver = server.url("/").toString(),
            accessToken = "test-token",
            roomId = "!room:example.com",
            client = client
        )

        assertFalse(result)
    }

    @Test
    fun `uses cached result on second call`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"algorithm":"m.megolm.v1.aes-sha2"}""")
                .build()
        )

        // First call triggers network request
        val first = RoomCryptoState.isRoomEncrypted(
            homeserver = server.url("/").toString(),
            accessToken = "test-token",
            roomId = "!room:example.com",
            client = client
        )
        assertTrue(first)

        // Second call should use cache (no second network request enqueued)
        val second = RoomCryptoState.isRoomEncrypted(
            homeserver = server.url("/").toString(),
            accessToken = "test-token",
            roomId = "!room:example.com",
            client = client
        )
        assertTrue(second)

        // Only one request should have been made
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `cache entry expires after TTL`() = runBlocking {
        // Insert expired entry directly into cache
        RoomCryptoState.cache["!room:example.com"] = RoomCryptoState.EncryptionInfo(
            encrypted = true,
            algorithm = "m.megolm.v1.aes-sha2",
            queriedAt = System.currentTimeMillis() - RoomCryptoState.CACHE_TTL_MS - 1
        )

        // Enqueue a response that says room is NOT encrypted now
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("""{"errcode":"M_NOT_FOUND"}""")
                .build()
        )

        val result = RoomCryptoState.isRoomEncrypted(
            homeserver = server.url("/").toString(),
            accessToken = "test-token",
            roomId = "!room:example.com",
            client = client
        )

        // Should have re-queried and got the updated (unencrypted) state
        assertFalse(result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `sends correct authorization header`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"algorithm":"m.megolm.v1.aes-sha2"}""")
                .build()
        )

        RoomCryptoState.isRoomEncrypted(
            homeserver = server.url("/").toString(),
            accessToken = "my-secret-token",
            roomId = "!room:example.com",
            client = client
        )

        val request = server.takeRequest()
        assertEquals("Bearer my-secret-token", request.headers["Authorization"])
    }

    @Test
    fun `requests correct URL path`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"algorithm":"m.megolm.v1.aes-sha2"}""")
                .build()
        )

        RoomCryptoState.isRoomEncrypted(
            homeserver = server.url("/").toString(),
            accessToken = "token",
            roomId = "!abc:example.com",
            client = client
        )

        val request = server.takeRequest()
        val requestLine = request.requestLine
        assertTrue(
            requestLine.contains("/_matrix/client/v3/rooms/") && requestLine.contains("/state/m.room.encryption"),
            "Expected request to contain the state endpoint, got: $requestLine"
        )
    }

    @Test
    fun `buildStateUrl normalizes homeserver`() {
        val url = RoomCryptoState.buildStateUrl(
            homeserver = "  https://matrix.org/  ",
            roomId = " !room:matrix.org "
        )
        assertEquals(
            "https://matrix.org/_matrix/client/v3/rooms/!room:matrix.org/state/m.room.encryption",
            url
        )
    }

    @Test
    fun `isExpired returns false for fresh entry`() {
        val info = RoomCryptoState.EncryptionInfo(
            encrypted = true,
            algorithm = "m.megolm.v1.aes-sha2",
            queriedAt = System.currentTimeMillis()
        )
        assertFalse(RoomCryptoState.isExpired(info))
    }

    @Test
    fun `isExpired returns true for old entry`() {
        val info = RoomCryptoState.EncryptionInfo(
            encrypted = true,
            algorithm = "m.megolm.v1.aes-sha2",
            queriedAt = System.currentTimeMillis() - RoomCryptoState.CACHE_TTL_MS - 1000
        )
        assertTrue(RoomCryptoState.isExpired(info))
    }
}
