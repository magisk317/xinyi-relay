package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.MatrixSetting
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * GitHub variant behavior verification.
 *
 * The GitHub flavor ships no E2EE code: the matrix-rust-sdk runtime arrives at
 * runtime from the matrix-e2ee-plugin APK. Until that plugin is installed the
 * plaintext MatrixE2eeUtils stub stays in charge, so a Matrix sender keeps
 * working without any crypto endpoints.
 */
class GithubVariantBehaviorTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `plugin loader starts in NOT_INSTALLED state`() {
        assertEquals(E2eeModuleStatus.NOT_INSTALLED, GithubPluginFeatureLoader.status)
    }

    @Test
    fun `plugin loader is not available before install`() {
        assertFalse(GithubPluginFeatureLoader.isAvailable)
    }

    @Test
    fun `plaintext send path sends m_room_message event type`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"event_id":"${"$"}ev123"}""")
                .build()
        )

        val setting = MatrixSetting(
            homeserver = server.url("/").toString(),
            accessToken = "test-token",
            roomId = "!test-room:example.com",
            messageType = "text",
        )
        val msgInfo = MsgInfo(
            type = "sms",
            from = "10086",
            content = "Test message content",
            date = Date(),
            simInfo = "SIM1",
        )

        MatrixUtils.sendMsg(setting, msgInfo)

        assertEquals(1, server.requestCount)

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("PUT", request.method)
        val requestPath = request.url.encodedPath
        assertTrue(
            requestPath.contains("/send/m.room.message/"),
            "Expected /send/m.room.message/ in path, got: $requestPath"
        )
        assertFalse(
            requestPath.contains("/send/m.room.encrypted/"),
            "Plaintext path must NOT use m.room.encrypted event type"
        )
    }

    @Test
    fun `plaintext send makes exactly one request with no crypto endpoints`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"event_id":"${"$"}ev456"}""")
                .build()
        )

        val setting = MatrixSetting(
            homeserver = server.url("/").toString(),
            accessToken = "test-token",
            roomId = "!encrypted-room:example.com",
            messageType = "text",
        )
        val msgInfo = MsgInfo(
            type = "sms",
            from = "Sender",
            content = "Hello encrypted world",
            date = Date(),
            simInfo = "SIM1",
        )

        MatrixUtils.sendMsg(setting, msgInfo)

        assertEquals(1, server.requestCount)

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        val requestPath = request.url.encodedPath

        val cryptoEndpoints = listOf(
            "/account/whoami",
            "/state/m.room.encryption",
            "/keys/upload",
            "/keys/query",
            "/sync",
            "/sendToDevice",
        )
        for (endpoint in cryptoEndpoints) {
            assertFalse(
                requestPath.contains(endpoint),
                "github variant without plugin must NOT call crypto endpoint: $endpoint, but path was: $requestPath"
            )
        }
    }

    @Test
    fun `plaintext send preserves message content without encryption`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"event_id":"${"$"}ev789"}""")
                .build()
        )

        val setting = MatrixSetting(
            homeserver = server.url("/").toString(),
            accessToken = "test-token",
            roomId = "!room:example.com",
            messageType = "text",
            titleTemplate = "Relay",
        )
        val msgInfo = MsgInfo(
            type = "sms",
            from = "10086",
            content = "Verification code: 123456",
            date = Date(),
            simInfo = "SIM1",
        )

        MatrixUtils.sendMsg(setting, msgInfo)

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        val body = request.body!!.utf8()

        assertTrue(body.contains("Verification code: 123456"))
        assertTrue(body.contains(""""msgtype":"m.text""""))
        assertFalse(body.contains("sender_key"), "No sender_key in plaintext body")
        assertFalse(body.contains("ciphertext"), "No ciphertext in plaintext body")
        assertFalse(
            body.contains("m.megolm.v1.aes-sha2"),
            "No encryption algorithm in plaintext body",
        )
    }

    @Test
    fun `plaintext send passes authorization header unchanged`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"event_id":"${"$"}ev000"}""")
                .build()
        )

        val setting = MatrixSetting(
            homeserver = server.url("/").toString(),
            accessToken = "my-access-token-123",
            roomId = "!room:example.com",
            messageType = "text",
        )
        val msgInfo = MsgInfo(
            type = "sms",
            from = "Test",
            content = "test",
            date = Date(),
            simInfo = "SIM1",
        )

        MatrixUtils.sendMsg(setting, msgInfo)

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("Bearer my-access-token-123", request.headers["Authorization"])
    }

    @Test
    fun `plaintext send does not check room state even for encrypted rooms`() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"event_id":"${"$"}evEnc"}""")
                .build()
        )

        val setting = MatrixSetting(
            homeserver = server.url("/").toString(),
            accessToken = "test-token",
            roomId = "!encrypted-room:matrix.org",
            messageType = "text",
        )
        val msgInfo = MsgInfo(
            type = "sms",
            from = "Bank",
            content = "Your OTP is 789012",
            date = Date(),
            simInfo = "SIM1",
        )

        MatrixUtils.sendMsg(setting, msgInfo)

        assertEquals(1, server.requestCount)

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("PUT", request.method)
        assertTrue(request.url.encodedPath.contains("/send/m.room.message/"))
    }

    @Test
    fun `github MatrixE2eeUtils has no shouldUseE2ee method`() {
        val methods = MatrixE2eeUtils::class.java.declaredMethods.map { it.name }
        assertFalse(
            methods.contains("shouldUseE2ee"),
            "github variant should not have shouldUseE2ee (routing decision is unconditional)"
        )
    }

    @Test
    fun `sender provider uninstall drops the plugin sender`() {
        // A plugin install replaces the null sender; uninstall must put it back
        // so MatrixE2eeUtils routes every send through the plaintext path.
        assertFalse(MatrixE2eeSenderProvider.isInstalled)

        MatrixE2eeSenderProvider.install(FakeE2eeSender)
        assertTrue(MatrixE2eeSenderProvider.isInstalled)
        assertNotNull(MatrixE2eeSenderProvider.getOrNull())

        MatrixE2eeSenderProvider.uninstall()

        assertFalse(MatrixE2eeSenderProvider.isInstalled)
        assertNull(MatrixE2eeSenderProvider.getOrNull())
    }

    private object FakeE2eeSender : MatrixE2eeSender {
        override suspend fun sendMsg(
            context: android.content.Context,
            setting: MatrixSetting,
            msgInfo: MsgInfo,
        ) = Unit
    }
}
