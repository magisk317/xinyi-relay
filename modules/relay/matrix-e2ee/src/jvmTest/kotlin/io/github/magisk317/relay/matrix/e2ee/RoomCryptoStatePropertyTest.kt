package io.github.magisk317.relay.matrix.e2ee

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient

/**
 * Property 1: 房间加密状态分类正确性
 *
 * Feature: matrix-e2ee-support, Property 1: 房间加密状态分类正确性
 *
 * **Validates: Requirements 1.2, 1.3, 1.4, 1.5**
 *
 * For any HTTP response to a `GET /rooms/{roomId}/state/m.room.encryption` request,
 * the room SHALL be classified as encryption-enabled if and only if the response is
 * HTTP 200 with a JSON body containing `algorithm` equal to `"m.megolm.v1.aes-sha2"`.
 * Authentication failures are propagated. Other conditions (non-200 status, missing algorithm
 * field, different algorithm value, malformed JSON, timeout) disable encryption classification.
 */
class RoomCryptoStatePropertyTest : FunSpec({

    fun newTestClient(
        connectTimeoutSeconds: Long = 5,
        readTimeoutSeconds: Long = 5,
        callTimeoutSeconds: Long = 6,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    suspend fun withRoomCryptoServer(
        client: OkHttpClient = newTestClient(),
        block: suspend (MockWebServer, OkHttpClient) -> Unit,
    ) {
        val server = MockWebServer()
        server.start()
        RoomCryptoState.clearCache()
        try {
            block(server, client)
        } finally {
            server.close()
            RoomCryptoState.clearCache()
        }
    }

    // --- Generators ---

    // Generator for non-authentication HTTP status codes that are NOT 200
    // Excludes no-body statuses that can desynchronize MockWebServer's response queue.
    val nonOkStatusCodeArb: Arb<Int> = Arb.element(
        (200..599).filter { it !in setOf(200, 204, 205, 304, 401, 403) }.toList()
    )

    // Generator for random JSON bodies that do NOT contain the supported algorithm
    val invalidJsonBodyArb: Arb<String> = arbitrary { rs ->
        val variant = Arb.int(0..5).bind()
        when (variant) {
            0 -> {
                // Valid JSON with a different algorithm
                val alg = Arb.element(
                    "m.olm.v1.curve25519-aes-sha2",
                    "m.megolm.v2.aes-sha2",
                    "custom.algorithm",
                    "aes-256-gcm",
                    "",
                ).bind()
                """{"algorithm":"$alg"}"""
            }
            1 -> {
                // Valid JSON with missing algorithm field
                val body = Arb.element(
                    """{"rotation_period_ms":604800000}""",
                    """{}""",
                    """{"algo":"m.megolm.v1.aes-sha2"}""",
                    """{"ALGORITHM":"m.megolm.v1.aes-sha2"}""",
                ).bind()
                body
            }
            2 -> {
                // Completely invalid JSON
                val s = Arb.string(1..50).bind()
                s
            }
            3 -> {
                // Empty body
                ""
            }
            4 -> {
                // JSON array (not an object)
                """["m.megolm.v1.aes-sha2"]"""
            }
            else -> {
                // Valid JSON with algorithm as non-string type
                Arb.element(
                    """{"algorithm":123}""",
                    """{"algorithm":null}""",
                    """{"algorithm":true}""",
                    """{"algorithm":["m.megolm.v1.aes-sha2"]}""",
                ).bind()
            }
        }
    }

    // Generator for valid encrypted room response body
    val validEncryptedBodyArb: Arb<String> = arbitrary {
        val extras = Arb.element(
            "",
            ""","rotation_period_ms":604800000""",
            ""","rotation_period_msgs":100""",
            ""","rotation_period_ms":604800000,"rotation_period_msgs":100""",
        ).bind()
        """{"algorithm":"m.megolm.v1.aes-sha2"$extras}"""
    }

    // --- Property Tests ---

    test("Property 1: HTTP 200 with algorithm m.megolm.v1.aes-sha2 returns encrypted true") {
        /**
         * **Validates: Requirements 1.2**
         *
         * For any valid HTTP 200 response with `algorithm == "m.megolm.v1.aes-sha2"`,
         * the room shall be classified as encryption-enabled.
         */
        withRoomCryptoServer { server, client ->
            checkAll(100, validEncryptedBodyArb) { body ->
                RoomCryptoState.clearCache()
                server.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .body(body)
                        .build()
                )

                val result = RoomCryptoState.isRoomEncrypted(
                    homeserver = server.url("/").toString(),
                    accessToken = "test-token",
                    roomId = "!room:example.com",
                    client = client,
                )

                result shouldBe true
            }
        }
    }

    test("Property 1: non-authentication HTTP error returns encrypted false") {
        /**
         * **Validates: Requirements 1.3, 1.5**
         *
         * For any non-authentication HTTP error, regardless of body content,
         * the room shall be classified as encryption-disabled.
         */
        withRoomCryptoServer { server, client ->
            checkAll(100, nonOkStatusCodeArb) { statusCode ->
                RoomCryptoState.clearCache()
                server.enqueue(
                    MockResponse.Builder()
                        .code(statusCode)
                        .body("""{"algorithm":"m.megolm.v1.aes-sha2"}""")
                        .build()
                )

                val result = RoomCryptoState.isRoomEncrypted(
                    homeserver = server.url("/").toString(),
                    accessToken = "test-token",
                    roomId = "!room:example.com",
                    client = client,
                )

                result shouldBe false
            }
        }
    }

    test("Property 1: HTTP 200 with invalid random JSON body returns encrypted false") {
        /**
         * **Validates: Requirements 1.4, 1.5**
         *
         * For any HTTP 200 response with a body that does not contain
         * `algorithm == "m.megolm.v1.aes-sha2"` (malformed JSON, missing field,
         * different algorithm, non-string value, etc.), the room shall be classified
         * as encryption-disabled.
         */
        withRoomCryptoServer { server, client ->
            checkAll(100, invalidJsonBodyArb) { body ->
                RoomCryptoState.clearCache()
                server.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .body(body)
                        .build()
                )

                val result = RoomCryptoState.isRoomEncrypted(
                    homeserver = server.url("/").toString(),
                    accessToken = "test-token",
                    roomId = "!room:example.com",
                    client = client,
                )

                result shouldBe false
            }
        }
    }

    test("Property 1: combined random status and body requires 200 plus supported algorithm") {
        /**
         * **Validates: Requirements 1.2, 1.3, 1.4, 1.5**
         *
         * For any combination of HTTP status code and JSON body, the classification
         * must follow the rule: encrypted=true iff (status==200 AND algorithm=="m.megolm.v1.aes-sha2").
         *
         * Each iteration uses a fresh MockWebServer instance to avoid response queue
         * desynchronization that occurs when sharing a single server across many iterations.
         */
        val statusCodeArb = Arb.int(200..599).filter { it !in setOf(204, 205, 304, 401, 403) }
        val bodyArb: Arb<String> = arbitrary {
            val useValid = Arb.element(true, false).bind()
            if (useValid) {
                """{"algorithm":"m.megolm.v1.aes-sha2"}"""
            } else {
                invalidJsonBodyArb.bind()
            }
        }

        checkAll(PropTestConfig(iterations = 50), statusCodeArb, bodyArb) { statusCode, body ->
            RoomCryptoState.clearCache()
            val server = MockWebServer()
            server.start()
            try {
                val roomId = "!room-${statusCode}-${body.hashCode()}:example.com"
                server.enqueue(
                    MockResponse.Builder()
                        .code(statusCode)
                        .addHeader("Content-Length", body.toByteArray().size.toString())
                        .body(body)
                        .build()
                )

                val client = newTestClient(
                    connectTimeoutSeconds = 2,
                    readTimeoutSeconds = 2,
                    callTimeoutSeconds = 3,
                )

                val result = RoomCryptoState.isRoomEncrypted(
                    homeserver = server.url("/").toString(),
                    accessToken = "test-token",
                    roomId = roomId,
                    client = client,
                )

                val expectedEncrypted = statusCode == 200 &&
                    body.contains(""""algorithm":"m.megolm.v1.aes-sha2"""")

                result shouldBe expectedEncrypted
            } finally {
                server.close()
            }
        }
    }
})
