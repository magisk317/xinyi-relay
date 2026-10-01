package io.github.magisk317.relay.contract.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class DeviceConfigCommandRequest(
    val baseRevision: Long,
    val summary: String,
    val mutation: JsonObject,
)

@Serializable
data class DeviceConfigAuditLogItem(
    val id: Long = 0L,
    val deviceId: Long = 0L,
    val commandId: Long? = null,
    val revision: Long = 0L,
    val eventType: String = "",
    val actorType: String = "",
    val actorId: Long = 0L,
    val summary: String = "",
    val createdAt: String = "",
)

@Serializable
data class DeviceConfigAuditLogsResponse(
    val logs: List<DeviceConfigAuditLogItem> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
)
