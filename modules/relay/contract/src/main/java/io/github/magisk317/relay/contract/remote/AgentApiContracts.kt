package io.github.magisk317.relay.contract.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AgentRegisterRequest(
    val bindCode: String,
    val deviceName: String,
    val deviceModel: String,
    val platform: String,
    val appVersion: String,
)

@Serializable
data class AgentRegisterResponse(
    val userId: Long = 0L,
    val deviceId: Long = 0L,
    val deviceToken: String = "",
)

@Serializable
data class HeartbeatRequest(
    val appVersion: String,
    val localAddresses: List<String>,
    val capabilities: Map<String, Boolean>,
)

@Serializable
data class AgentConfigMirrorRequest(
    val localRevision: Long,
    val mirrorContent: JsonObject,
    val summary: String = "",
)

@Serializable
data class AgentConfigCommandsPullRequest(
    val localRevision: Long,
)

@Serializable
data class AgentConfigCommandsPullResponse(
    val deviceId: Long = 0L,
    val revision: Long = 0L,
    val mirrorContent: JsonObject? = null,
    val pendingCommands: List<DeviceConfigCommandResponse> = emptyList(),
    val updatedAt: String = "",
)

@Serializable
data class DeviceConfigCommandResponse(
    val id: Long = 0L,
    val baseRevision: Long = 0L,
    val targetRevision: Long = 0L,
    val mutation: JsonObject,
    val summary: String = "",
    val actorType: String = "",
    val actorId: Long = 0L,
    val status: String = "",
    val failureReason: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val appliedAt: String? = null,
)

@Serializable
data class DeviceConfigStateResponse(
    val deviceId: Long = 0L,
    val revision: Long = 0L,
    val mirrorContent: JsonObject? = null,
    val pendingCommands: List<DeviceConfigCommandResponse> = emptyList(),
    val updatedAt: String = "",
)

@Serializable
data class AgentConfigCommandsAckRequest(
    val commandId: Long,
    val status: String,
    val appliedRevision: Long,
    val failureReason: String = "",
    val mirrorContent: JsonObject,
)

@Serializable
data class RelayRecordWire(
    val eventId: String,
    val recordType: String,
    val sender: String,
    val body: String,
    val smsCode: String,
    val packageName: String,
    val msgType: Int,
    val callType: Int,
    val occurredAt: String,
    val metadata: JsonObject,
)

@Serializable
data class RelayRecordsBatchRequest(
    val records: List<RelayRecordWire>,
)

@Serializable
data class RelayRecordsBatchResponse(
    val inserted: Long = 0L,
)
