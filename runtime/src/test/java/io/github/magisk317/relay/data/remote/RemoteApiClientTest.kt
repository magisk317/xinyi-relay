package io.github.magisk317.relay.data.remote

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteApiClientTest {

    @Test
    fun pushConfigSnapshot_conflictReturnsSnapshotWithoutThrowing() {
        val client = RemoteApiClient(
            client = fakeHttpClient(
                code = 409,
                body = """{"revision":7,"snapshot":{"general":{"moduleEnabled":true}}}""",
            ),
        )

        val result = client.pushConfigSnapshot(
            baseUrl = "https://relay.example",
            deviceToken = "token-1",
            request = ConfigSnapshotRequest(
                baseRevision = 1L,
                snapshot = buildJsonObject {},
            ),
        )

        assertTrue(result is ConfigSnapshotPushResult.Conflict)
        val conflict = result as ConfigSnapshotPushResult.Conflict
        assertEquals(7L, conflict.payload.revision)
        assertTrue(
            conflict.payload.snapshot!!
                .getValue("general").jsonObject
                .getValue("moduleEnabled").jsonPrimitive.boolean,
        )
    }

    @Test
    fun uploadRelayRecords_usesBearerTokenAndBatchEndpoint() {
        var observedPath = ""
        var observedAuthorization = ""
        var observedBody = ""
        val client = RemoteApiClient(
            client = fakeHttpClient { request ->
                observedPath = request.url.encodedPath
                observedAuthorization = request.header("Authorization").orEmpty()
                observedBody = request.bodyText()
                httpResponse(request, code = 204, body = "")
            },
        )

        client.uploadRelayRecords(
            baseUrl = "https://relay.example",
            deviceToken = "token-1",
            request = RelayRecordsBatchRequest(
                records = listOf(
                    RelayRecordWire(
                        eventId = "event-1",
                        recordType = "sms_code",
                        sender = "sender",
                        body = "body",
                        smsCode = "1234",
                        packageName = "",
                        msgType = 0,
                        callType = 0,
                        occurredAt = "2026-05-16T00:00:00Z",
                        metadata = buildJsonObject {},
                    ),
                ),
            ),
        )

        assertEquals("/api/v1/agent/records:batch", observedPath)
        assertEquals("Bearer token-1", observedAuthorization)
        assertTrue(observedBody.contains(""""records""""))
        assertTrue(observedBody.contains(""""eventId":"event-1""""))
    }

    private fun fakeHttpClient(
        code: Int,
        body: String,
    ): OkHttpClient {
        return fakeHttpClient { request -> httpResponse(request, code, body) }
    }

    private fun fakeHttpClient(block: (okhttp3.Request) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> block(chain.request()) })
            .build()
    }

    private fun httpResponse(
        request: okhttp3.Request,
        code: Int,
        body: String,
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    private fun okhttp3.Request.bodyText(): String {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return buffer.readUtf8()
    }
}
