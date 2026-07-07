package io.github.magisk317.relay.data.remote

import io.github.magisk317.relay.contract.remote.AgentConfigCommandsAckRequest
import io.github.magisk317.relay.contract.remote.AgentConfigCommandsPullRequest
import io.github.magisk317.relay.contract.remote.AgentConfigCommandsPullResponse
import io.github.magisk317.relay.contract.remote.AgentConfigMirrorRequest
import io.github.magisk317.relay.contract.remote.AgentRegisterRequest
import io.github.magisk317.relay.contract.remote.AgentRegisterResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigStateResponse
import io.github.magisk317.relay.contract.remote.HeartbeatRequest
import io.github.magisk317.relay.contract.remote.RelayRecordsBatchRequest
import io.github.magisk317.relay.contract.json.RelayJson
import io.github.magisk317.relay.net.RelayHttpClients
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface RemoteAgentApi {
    fun registerDevice(baseUrl: String, request: AgentRegisterRequest): AgentRegisterResponse

    fun sendHeartbeat(
        baseUrl: String,
        deviceToken: String,
        request: HeartbeatRequest,
    )

    fun pushConfigMirror(
        baseUrl: String,
        deviceToken: String,
        request: AgentConfigMirrorRequest,
    ): DeviceConfigStateResponse

    fun pullConfigCommands(
        baseUrl: String,
        deviceToken: String,
        request: AgentConfigCommandsPullRequest,
    ): AgentConfigCommandsPullResponse

    fun ackConfigCommand(
        baseUrl: String,
        deviceToken: String,
        request: AgentConfigCommandsAckRequest,
    ): DeviceConfigCommandResponse

    fun uploadRelayRecords(
        baseUrl: String,
        deviceToken: String,
        request: RelayRecordsBatchRequest,
    )
}

internal class RemoteApiClient(
    private val client: OkHttpClient = RelayHttpClients.default,
) : RemoteAgentApi {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun registerDevice(baseUrl: String, request: AgentRegisterRequest): AgentRegisterResponse {
        return executeJson(
            request = Request.Builder()
                .url("$baseUrl/api/v1/agent/register")
                .post(jsonBody(AgentRegisterRequest.serializer(), request))
                .build(),
            deserializer = AgentRegisterResponse.serializer(),
            failureLabel = "bind",
        )
    }

    override fun sendHeartbeat(
        baseUrl: String,
        deviceToken: String,
        request: HeartbeatRequest,
    ) {
        executeEmpty(
            request = Request.Builder()
                .url("$baseUrl/api/v1/agent/heartbeat")
                .bearer(deviceToken)
                .post(jsonBody(HeartbeatRequest.serializer(), request))
                .build(),
            failureLabel = "heartbeat",
        )
    }

    override fun pushConfigMirror(
        baseUrl: String,
        deviceToken: String,
        request: AgentConfigMirrorRequest,
    ): DeviceConfigStateResponse {
        return executeJson(
            request = Request.Builder()
                .url("$baseUrl/api/v1/agent/config/mirror")
                .bearer(deviceToken)
                .post(jsonBody(AgentConfigMirrorRequest.serializer(), request))
                .build(),
            deserializer = DeviceConfigStateResponse.serializer(),
            failureLabel = "push_mirror",
        )
    }

    override fun pullConfigCommands(
        baseUrl: String,
        deviceToken: String,
        request: AgentConfigCommandsPullRequest,
    ): AgentConfigCommandsPullResponse {
        return executeJson(
            request = Request.Builder()
                .url("$baseUrl/api/v1/agent/config/commands:pull")
                .bearer(deviceToken)
                .post(jsonBody(AgentConfigCommandsPullRequest.serializer(), request))
                .build(),
            deserializer = AgentConfigCommandsPullResponse.serializer(),
            failureLabel = "pull_commands",
        )
    }

    override fun ackConfigCommand(
        baseUrl: String,
        deviceToken: String,
        request: AgentConfigCommandsAckRequest,
    ): DeviceConfigCommandResponse {
        return executeJson(
            request = Request.Builder()
                .url("$baseUrl/api/v1/agent/config/commands:ack")
                .bearer(deviceToken)
                .post(jsonBody(AgentConfigCommandsAckRequest.serializer(), request))
                .build(),
            deserializer = DeviceConfigCommandResponse.serializer(),
            failureLabel = "ack_command",
        )
    }

    override fun uploadRelayRecords(
        baseUrl: String,
        deviceToken: String,
        request: RelayRecordsBatchRequest,
    ) {
        executeEmpty(
            request = Request.Builder()
                .url("$baseUrl/api/v1/agent/records:batch")
                .bearer(deviceToken)
                .post(jsonBody(RelayRecordsBatchRequest.serializer(), request))
                .build(),
            failureLabel = "records_upload",
        )
    }

    private fun <T> jsonBody(serializer: SerializationStrategy<T>, payload: T) =
        RelayJson.encode(serializer, payload).toRequestBody(jsonMediaType)

    private fun <T> executeJson(
        request: Request,
        deserializer: DeserializationStrategy<T>,
        failureLabel: String,
    ): T {
        client.newCall(request).execute().use { response ->
            if (response.code == HTTP_UNAUTHORIZED) {
                throw DeviceTokenExpiredException(errorMessage(response.body.string(), failureLabel, response.code))
            }
            if (!response.isSuccessful) {
                throw IllegalStateException(errorMessage(response.body.string(), failureLabel, response.code))
            }
            return RelayJson.decode(deserializer, response.body.string())
        }
    }

    private fun executeEmpty(request: Request, failureLabel: String) {
        client.newCall(request).execute().use { response ->
            if (response.code == HTTP_UNAUTHORIZED) {
                throw DeviceTokenExpiredException(errorMessage(response.body.string(), failureLabel, response.code))
            }
            if (!response.isSuccessful) {
                throw IllegalStateException(errorMessage(response.body.string(), failureLabel, response.code))
            }
        }
    }

    private fun errorMessage(responseText: String, failureLabel: String, responseCode: Int): String {
        return responseText.ifBlank { "$failureLabel failed: $responseCode" }
    }

    private fun Request.Builder.bearer(deviceToken: String): Request.Builder {
        return header("Authorization", "Bearer $deviceToken")
    }
}

private const val HTTP_UNAUTHORIZED = 401

/**
 * Thrown when the backend returns 401, indicating the device token has expired
 * or been revoked. Callers should attempt re-registration.
 */
class DeviceTokenExpiredException(message: String) : IllegalStateException(message)
