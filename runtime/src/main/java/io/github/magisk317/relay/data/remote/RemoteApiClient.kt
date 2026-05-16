package io.github.magisk317.relay.data.remote

import io.github.magisk317.relay.contract.json.LegacyGsonJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class RemoteApiClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: LegacyGsonJson = LegacyGsonJson,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun registerDevice(baseUrl: String, request: AgentRegisterRequest): AgentRegisterResponse {
        return executeJson(
            request = Request.Builder()
                .url("$baseUrl/api/v1/agent/register")
                .post(jsonBody(request))
                .build(),
            responseClass = AgentRegisterResponse::class.java,
            failureLabel = "bind",
        )
    }

    fun sendHeartbeat(
        baseUrl: String,
        deviceToken: String,
        request: HeartbeatRequest,
    ) {
        executeEmpty(
            request = Request.Builder()
                .url("$baseUrl/api/v1/agent/heartbeat")
                .bearer(deviceToken)
                .post(jsonBody(request))
                .build(),
            failureLabel = "heartbeat",
        )
    }

    fun pullConfigSnapshot(baseUrl: String, deviceToken: String): ConfigSnapshotResponse {
        return executeJson(
            request = Request.Builder()
                .url("$baseUrl/api/v1/config/snapshot")
                .bearer(deviceToken)
                .get()
                .build(),
            responseClass = ConfigSnapshotResponse::class.java,
            failureLabel = "pull",
        )
    }

    fun pushConfigSnapshot(
        baseUrl: String,
        deviceToken: String,
        request: ConfigSnapshotRequest,
    ): ConfigSnapshotPushResult {
        val httpRequest = Request.Builder()
            .url("$baseUrl/api/v1/config/snapshot")
            .bearer(deviceToken)
            .put(jsonBody(request))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val responseText = response.body.string()
            if (response.code == HTTP_CONFLICT) {
                return ConfigSnapshotPushResult.Conflict(parseConfigSnapshot(responseText))
            }
            if (!response.isSuccessful) {
                throw IllegalStateException(responseText.ifBlank { "push failed: ${response.code}" })
            }
            return ConfigSnapshotPushResult.Success(parseConfigSnapshot(responseText))
        }
    }

    fun uploadRelayRecords(
        baseUrl: String,
        deviceToken: String,
        request: RelayRecordsBatchRequest,
    ) {
        executeEmpty(
            request = Request.Builder()
                .url("$baseUrl/api/v1/agent/records:batch")
                .bearer(deviceToken)
                .post(jsonBody(request))
                .build(),
            failureLabel = "records_upload",
        )
    }

    private fun jsonBody(payload: Any) = json.toJson(payload).toRequestBody(jsonMediaType)

    private fun <T> executeJson(
        request: Request,
        responseClass: Class<T>,
        failureLabel: String,
    ): T {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(errorMessage(response.body.string(), failureLabel, response.code))
            }
            return json.fromJson(response.body.charStream(), responseClass)
        }
    }

    private fun executeEmpty(request: Request, failureLabel: String) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(errorMessage(response.body.string(), failureLabel, response.code))
            }
        }
    }

    private fun parseConfigSnapshot(responseText: String): ConfigSnapshotResponse {
        return json.fromJson(responseText, ConfigSnapshotResponse::class.java)
    }

    private fun errorMessage(responseText: String, failureLabel: String, responseCode: Int): String {
        return responseText.ifBlank { "$failureLabel failed: $responseCode" }
    }

    private fun Request.Builder.bearer(deviceToken: String): Request.Builder {
        return header("Authorization", "Bearer $deviceToken")
    }
}

internal sealed interface ConfigSnapshotPushResult {
    data class Success(val payload: ConfigSnapshotResponse) : ConfigSnapshotPushResult
    data class Conflict(val payload: ConfigSnapshotResponse) : ConfigSnapshotPushResult
}

private const val HTTP_CONFLICT = 409
