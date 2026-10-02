package io.github.magisk317.relay.desktop.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Wire and domain models for the desktop client.
 *
 * Every field name here is part of the wire contract with the Go backend and
 * with the Android agent; the camelCase names match the Rust `serde`
 * `rename_all = "camelCase"` output exactly.
 */
@Serializable
data class Device(
    val id: Long = 0,
    val userId: Long = 0,
    val deviceName: String = "",
    val deviceModel: String = "",
    val platform: String = "android",
    val appVersion: String = "",
    val displayName: String = "",
    val enabled: Boolean = true,
    val revokedAt: String? = null,
    val lastSeenAt: String? = null,
    val localAddresses: JsonElement = JsonObject(emptyMap<String, JsonElement>()),
    val capabilities: JsonElement = JsonObject(emptyMap<String, JsonElement>()),
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class Record(
    val id: Long = 0,
    val deviceId: Long = 0,
    val eventId: String? = null,
    val recordType: String = "",
    val sender: String = "",
    val body: String = "",
    val smsCode: String = "",
    val packageName: String = "",
    val metadata: JsonElement = JsonObject(emptyMap<String, JsonElement>()),
    val msgType: Int = 0,
    val callType: Int = 0,
    val occurredAt: String = "",
    val uploadedAt: String = "",
)

const val COMMAND_STATUS_PENDING = "pending"
const val COMMAND_STATUS_APPLIED = "applied"
const val COMMAND_STATUS_FAILED = "failed"
const val COMMAND_STATUS_STALE = "stale"

/**
 * Failure reason attached when a queued command is folded away because a newer
 * revision already reached the mirror. The legacy Rust store kept such rows in
 * the queue forever; the Kotlin store marks them instead.
 */
const val FAILURE_REASON_STALE_BASE_REVISION = "stale_base_revision"

@Serializable
data class DeviceConfigCommand(
    val id: Long = 0,
    val baseRevision: Long = 0,
    val targetRevision: Long = 0,
    val mutation: JsonElement = JsonObject(emptyMap<String, JsonElement>()),
    val summary: String = "",
    val actorType: String = "desktop",
    val actorId: Long = 0,
    val status: String = COMMAND_STATUS_PENDING,
    val failureReason: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val appliedAt: String? = null,
)

@Serializable
data class DeviceConfigAuditLog(
    val id: Long = 0,
    val deviceId: Long = 0,
    val commandId: Long? = null,
    val revision: Long = 0,
    val eventType: String = "",
    val actorType: String = "desktop",
    val actorId: Long = 0,
    val summary: String = "",
    val createdAt: String = "",
)

/**
 * Config state as exchanged with the backend.
 *
 * `snapshot` is serialized as `mirrorContent`, matching
 * `store.rs` `#[serde(rename = "mirrorContent")]`.
 */
@Serializable
data class DeviceConfigState(
    val deviceId: Long = 0,
    val revision: Long = 0,
    @SerialName("mirrorContent")
    val snapshot: JsonElement = JsonObject(emptyMap<String, JsonElement>()),
    val pendingCommands: List<DeviceConfigCommand> = emptyList(),
    val updatedAt: String? = null,
)

@Serializable
data class BindCode(
    val code: String,
    val expiresAt: String,
)

@Serializable
data class SystemInfo(
    val service: String = "",
    val appEnv: String = "",
    val localBaseUrl: String = "",
    val publicBaseUrl: String = "",
    val databaseReady: Boolean = false,
    val userCount: Long = 0,
    val time: String = "",
)

data class Paginated<T>(
    val items: List<T>,
    val limit: Int,
    val offset: Int,
)

data class RecordSyncResult(
    val inserted: Int,
    val updated: Int,
    val deleted: Int,
)

/**
 * Outcome of a config sync round. Mirrors `sync.rs::SyncResult`.
 */
sealed interface SyncResult {
    data object UpToDate : SyncResult

    data class Pulled(val newRevision: Long) : SyncResult

    data class Pushed(val newRevision: Long) : SyncResult

    data class Conflict(val localRevision: Long, val remoteRevision: Long) : SyncResult
}

data class SyncReport(
    val config: SyncResult,
    val devicesSynced: Int = 0,
    val recordsSynced: Int = 0,
    val error: String? = null,
)

/**
 * Local/Remote/Hybrid run modes of the desktop client.
 */
enum class RunMode {
    Remote,
    Local,
    Hybrid,
}
