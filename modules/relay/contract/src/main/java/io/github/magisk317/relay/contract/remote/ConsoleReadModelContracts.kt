package io.github.magisk317.relay.contract.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class DeviceItem(
    val id: Long = 0L,
    val userId: Long = 0L,
    val deviceName: String = "",
    val deviceModel: String = "",
    val platform: String = "",
    val appVersion: String = "",
    val displayName: String = "",
    val enabled: Boolean = false,
    val revokedAt: String? = null,
    val lastSeenAt: String? = null,
    val localAddresses: JsonObject,
    val capabilities: JsonObject,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class DevicesResponse(
    val devices: List<DeviceItem> = emptyList(),
)

@Serializable
data class RelayRecord(
    val id: Long = 0L,
    val deviceId: Long = 0L,
    val eventId: String? = null,
    val recordType: String = "",
    val sender: String = "",
    val body: String = "",
    val smsCode: String = "",
    val packageName: String = "",
    val msgType: Int = 0,
    val callType: Int = 0,
    val occurredAt: String = "",
    val uploadedAt: String = "",
    val metadata: JsonObject,
)

@Serializable
data class RecordsResponse(
    val records: List<RelayRecord> = emptyList(),
    val limit: Long = 0L,
    val offset: Long = 0L,
)
