package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.MatrixSetting
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration test for the encrypted send chain.
 *
 * Tests the full routing logic from initialization through room state detection
 * to encrypted send / plaintext fallback, using MockWebServer to simulate the homeserver.
 *
 * Since the matrix-rust-sdk native libraries are not available in JVM unit tests,
 * the "encrypted send" path cannot be tested end-to-end here. Instead, we verify:
 * 1. Room encryption state detection correctly queries the homeserver
 * 2. Plaintext fallback when room is NOT encrypted (Requirements 5.2, 8.4)
 * 3. Plaintext fallback when E2EE module is unavailable (Requirements 5.1, 8.3)
 * 4. Consecutive sends reuse cached room state (Requirements 1.6)
 * 5. Routing decision logic is correct for all input combinations (Requirements 8.2, 8.3, 8.4)
 *
 * _Requirements: 4.1, 4.2, 4.3, 4.4, 5.1, 5.2, 8.2, 8.3, 8.4_
 */
class E2eeSendChainIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    private val testMsgInfo = MsgInfo(
        type = "sms",
        from = "10086",
        content = "Test message content",
        date = Date(),
        simInfo = "SIM1",
    )

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

    // ============================================================
    // 1. Full chain: room state detection → plaintext routing
    // ============================================================

    @Test
    fun `room state query returns encrypted room - routing decision is E2EE`() = runBlocking {
        // Simulate homeserver reporting room IS encrypted
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"algorithm":"m.megolm.v1.aes-sha2","rotation_period_ms":604800000}""")
                .build()
        )

        val homeserver = server.url("/").toString()
        val isEncrypted = RoomCryptoState.isRoomEncrypted(
            homeserver = homeserver,
            accessToken = "test-token",
            roomId = "!encrypted-room:example.com",
            client = client,
        )

        assertTrue(isEncrypted)
        // With E2EE available and room encrypted → should route to E2EE
        assertTrue(MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable = true, roomEncrypted = isEncrypted))
    }

    @Test
    fun `room state query returns unencrypted room - routing decision is plaintext`() = runBlocking {
        // Simulate homeserver reporting room is NOT encrypted (404)
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("""{"errcode":"M_NOT_FOUND","error":"Event not found"}""")
                .build()
        )

        val homeserver = server.url("/").toString()
        val isEncrypted = RoomCryptoState.isRoomEncrypted(
            homeserver = homeserver,
            accessToken = "test-token",
            roomId = "!plain-room:example.com",
            client = client,
        )

        assertFalse(isEncrypted)
        // With E2EE available but room NOT encrypted → should route to plaintext
        assertFalse(MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable = true, roomEncrypted = isEncrypted))
    }

    @Test
    fun `plaintext send path constructs correct HTTP request to homeserver`() = runBlocking {
        // Enqueue a success response for the plaintext send
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"event_id":"${'$'}abc123:example.com"}""")
                .build()
        )

        val homeserver = server.url("/").toString()
        val setting = MatrixSetting(
            homeserver = homeserver,
            accessToken = "test-access-token",
            roomId = "!room:example.com",
            messageType = "text",
            titleTemplate = "Test Title",
        )

        MatrixUtils.sendMsg(setting, testMsgInfo)

        // Verify the HTTP request
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        val requestLine = request.requestLine
        assertTrue(requestLine.contains("/_matrix/client/v3/rooms/"))
        assertTrue(requestLine.contains("/send/m.room.message/"))
        assertTrue(requestLine.startsWith("PUT"))
        assertEquals("Bearer test-access-token", request.headers["Authorization"])

        // Verify message body contains the content
        val body = request.body!!.utf8()
        assertTrue(body.contains("Test message content"))
        assertTrue(body.contains("Test Title"))
    }

    // ============================================================
    // 2. Plaintext fallback when room is not encrypted
    // ============================================================

    @Test
    fun `unencrypted room sends m-room-message via plaintext path`() = runBlocking {
        // First request: room state query returns 404 (not encrypted)
        // Second request: plaintext send success
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("""{"errcode":"M_NOT_FOUND"}""")
                .build()
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"event_id":"${'$'}evt1:example.com"}""")
                .build()
        )

        val homeserver = server.url("/").toString()

        // Detect room state
        val isEncrypted = RoomCryptoState.isRoomEncrypted(
            homeserver = homeserver,
            accessToken = "token",
            roomId = "!plain-room:example.com",
            client = client,
        )
        assertFalse(isEncrypted)

        // Send via plaintext
        val setting = MatrixSetting(
            homeserver = homeserver,
            accessToken = "token",
            roomId = "!plain-room:example.com",
            messageType = "text",
        )
        MatrixUtils.sendMsg(setting, testMsgInfo)

        // Verify: 2 requests total (state query + plaintext send)
        assertEquals(2, server.requestCount)

        // First request is the room state query
        val stateRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(stateRequest.requestLine.contains("/state/m.room.encryption"))

        // Second request is the plaintext send
        val sendRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(sendRequest.requestLine.contains("/send/m.room.message/"))
    }

    // ============================================================
    // 3. E2EE module unavailability → plaintext fallback
    // ============================================================

    @Test
    fun `routing decision is plaintext when E2EE module is unavailable regardless of room state`() {
        // Requirement 5.1, 8.3: E2EE unavailable → always plaintext
        assertFalse(MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable = false, roomEncrypted = true))
        assertFalse(MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable = false, roomEncrypted = false))
    }

    @Test
    fun `routing decision is E2EE only when both conditions met`() {
        // Requirement 8.2: E2EE available AND room encrypted → E2EE path
        assertTrue(MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable = true, roomEncrypted = true))
        // Requirement 8.4: room not encrypted → plaintext even if E2EE available
        assertFalse(MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable = true, roomEncrypted = false))
    }

    // ============================================================
    // 4. Consecutive sends reuse cached room state
    // ============================================================

    @Test
    fun `consecutive sends reuse cached room encryption state`() = runBlocking {
        // Only enqueue ONE room state response
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"algorithm":"m.megolm.v1.aes-sha2"}""")
                .build()
        )

        val homeserver = server.url("/").toString()
        val roomId = "!cached-room:example.com"

        // First call: queries the server
        val result1 = RoomCryptoState.isRoomEncrypted(
            homeserver = homeserver,
            accessToken = "token",
            roomId = roomId,
            client = client,
        )
        assertTrue(result1)

        // Second call: should use cache (no additional HTTP request)
        val result2 = RoomCryptoState.isRoomEncrypted(
            homeserver = homeserver,
            accessToken = "token",
            roomId = roomId,
            client = client,
        )
        assertTrue(result2)

        // Third call: still cached
        val result3 = RoomCryptoState.isRoomEncrypted(
            homeserver = homeserver,
            accessToken = "token",
            roomId = roomId,
            client = client,
        )
        assertTrue(result3)

        // Only 1 HTTP request was made (cache served subsequent calls)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `consecutive sends to different rooms make separate queries`() = runBlocking {
        // Enqueue responses for two different rooms
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"algorithm":"m.megolm.v1.aes-sha2"}""")
                .build()
        )
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("""{"errcode":"M_NOT_FOUND"}""")
                .build()
        )

        val homeserver = server.url("/").toString()

        val result1 = RoomCryptoState.isRoomEncrypted(
            homeserver = homeserver,
            accessToken = "token",
            roomId = "!room-a:example.com",
            client = client,
        )
        assertTrue(result1)

        val result2 = RoomCryptoState.isRoomEncrypted(
            homeserver = homeserver,
            accessToken = "token",
            roomId = "!room-b:example.com",
            client = client,
        )
        assertFalse(result2)

        // 2 requests (one per room)
        assertEquals(2, server.requestCount)

        // Subsequent calls use cache
        val result1Again = RoomCryptoState.isRoomEncrypted(
            homeserver = homeserver,
            accessToken = "token",
            roomId = "!room-a:example.com",
            client = client,
        )
        assertTrue(result1Again)
        assertEquals(2, server.requestCount) // still 2, no new request
    }

    // ============================================================
    // 5. Encryption failure → plaintext fallback (verified via routing logic)
    // ============================================================

    @Test
    fun `categorizeFailureReason returns encryption_timeout for timeout exceptions`() {
        val timeoutError = java.net.SocketTimeoutException("read timed out")
        assertEquals("encryption_timeout", MatrixE2eeUtils.categorizeFailureReason(timeoutError))
    }

    @Test
    fun `categorizeFailureReason returns encryption_error for general exceptions`() {
        val generalError = RuntimeException("something went wrong")
        assertEquals("encryption_error", MatrixE2eeUtils.categorizeFailureReason(generalError))
    }

    @Test
    fun `room state network failure treats room as unencrypted for safe fallback`() = runBlocking {
        // Simulate network timeout by using an unreachable address
        val unreachableHomeserver = "http://192.0.2.1:1" // RFC 5737 TEST-NET, guaranteed unreachable

        val result = RoomCryptoState.isRoomEncrypted(
            homeserver = unreachableHomeserver,
            accessToken = "token",
            roomId = "!unreachable-room:example.com",
            client = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build(),
        )

        // Network failure → treated as unencrypted → plaintext fallback safe
        assertFalse(result)
    }

    @Test
    fun `room state HTTP 500 treats room as unencrypted`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(500)
                .body("""{"errcode":"M_UNKNOWN","error":"Internal Server Error"}""")
                .build()
        )

        val result = RoomCryptoState.isRoomEncrypted(
            homeserver = server.url("/").toString(),
            accessToken = "token",
            roomId = "!error-room:example.com",
            client = client,
        )

        assertFalse(result)
    }

    // ============================================================
    // 6. Full integration: room detection + plaintext send chain
    // ============================================================

    @Test
    fun `full chain - detect unencrypted room then send plaintext successfully`() = runBlocking {
        // Response 1: room state → not encrypted
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("""{"errcode":"M_NOT_FOUND"}""")
                .build()
        )
        // Response 2: plaintext message send → success
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"event_id":"${'$'}msg1:example.com"}""")
                .build()
        )

        val homeserver = server.url("/").toString()
        val setting = MatrixSetting(
            homeserver = homeserver,
            accessToken = "my-token",
            roomId = "!target-room:example.com",
            messageType = "markdown",
            titleTemplate = "Integration Test",
        )

        // Step 1: Detect room state
        val encrypted = RoomCryptoState.isRoomEncrypted(
            homeserver = homeserver,
            accessToken = setting.accessToken,
            roomId = setting.roomId,
            client = client,
        )
        assertFalse(encrypted)

        // Step 2: Since room is not encrypted, send via plaintext
        assertFalse(MatrixE2eeUtils.shouldUseE2ee(e2eeAvailable = true, roomEncrypted = encrypted))
        MatrixUtils.sendMsg(setting, testMsgInfo)

        // Verify the chain
        assertEquals(2, server.requestCount)

        // First request: room state query
        val stateReq = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(stateReq.requestLine.contains("/state/m.room.encryption"))
        assertTrue(stateReq.requestLine.startsWith("GET"))

        // Second request: plaintext send
        val sendReq = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(sendReq.requestLine.contains("/send/m.room.message/"))
        assertTrue(sendReq.requestLine.startsWith("PUT"))
        val sendBody = sendReq.body!!.utf8()
        assertTrue(sendBody.contains("Test message content"))
        assertTrue(sendBody.contains("m.text"))
    }

    @Test
    fun `full chain - consecutive plaintext sends reuse room state cache`() = runBlocking {
        // Only 1 room state response + 2 plaintext send responses
        server.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("""{"errcode":"M_NOT_FOUND"}""")
                .build()
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"event_id":"${'$'}msg1:example.com"}""")
                .build()
        )
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"event_id":"${'$'}msg2:example.com"}""")
                .build()
        )

        val homeserver = server.url("/").toString()
        val setting = MatrixSetting(
            homeserver = homeserver,
            accessToken = "token",
            roomId = "!reuse-room:example.com",
        )

        // First send: queries room state + sends plaintext
        val enc1 = RoomCryptoState.isRoomEncrypted(
            homeserver = homeserver,
            accessToken = setting.accessToken,
            roomId = setting.roomId,
            client = client,
        )
        assertFalse(enc1)
        MatrixUtils.sendMsg(setting, testMsgInfo)

        // Second send: reuses cached room state + sends plaintext
        val enc2 = RoomCryptoState.isRoomEncrypted(
            homeserver = homeserver,
            accessToken = setting.accessToken,
            roomId = setting.roomId,
            client = client,
        )
        assertFalse(enc2)
        MatrixUtils.sendMsg(setting, testMsgInfo)

        // Total: 1 room state query + 2 plaintext sends = 3 requests
        assertEquals(3, server.requestCount)
    }

    // ============================================================
    // 7. Store path derivation (integration with routing)
    // ============================================================

    @Test
    fun `sha256Hex is deterministic - same userId always produces same hash`() {
        val userId1 = "@alice:matrix.org"
        val hash1 = MatrixE2eeUtils.sha256Hex(userId1)
        val hash2 = MatrixE2eeUtils.sha256Hex(userId1)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `sha256Hex produces different hashes for different userIds`() {
        val hash1 = MatrixE2eeUtils.sha256Hex("@alice:matrix.org")
        val hash2 = MatrixE2eeUtils.sha256Hex("@bob:matrix.org")
        assertTrue(hash1 != hash2)
    }
}
