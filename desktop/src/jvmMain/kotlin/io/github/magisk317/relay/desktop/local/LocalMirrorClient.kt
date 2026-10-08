package io.github.magisk317.relay.desktop.local

import io.github.magisk317.relay.contract.remote.BindCodeResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigAuditLogItem
import io.github.magisk317.relay.contract.remote.DeviceConfigAuditLogsResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandRequest
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigStateResponse
import io.github.magisk317.relay.contract.remote.DeviceItem
import io.github.magisk317.relay.contract.remote.DevicesResponse
import io.github.magisk317.relay.contract.remote.PatchDeviceRequest
import io.github.magisk317.relay.contract.remote.RecordsResponse
import io.github.magisk317.relay.contract.remote.RelayRecord
import io.github.magisk317.relay.contract.remote.SystemInfoResponse
import io.github.magisk317.relay.desktop.core.model.Device
import io.github.magisk317.relay.desktop.core.model.DeviceConfigAuditLog
import io.github.magisk317.relay.desktop.core.model.DeviceConfigCommand
import io.github.magisk317.relay.desktop.core.model.Record
import io.github.magisk317.relay.desktop.core.store.DesktopLocalStore
import io.github.magisk317.relay.desktop.remote.ConsoleApiException
import io.github.magisk317.relay.desktop.remote.ConsoleDataClient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Console data client served from the local SQLite mirror — the read and
 * write path of the Local run mode, the KMP counterpart of the Rust shell's
 * `if mode == RunMode::Local { ... local store ... }` short-circuits.
 *
 * Every method answers with the same `relay:contract` DTOs the remote client
 * returns, so no page knows which side it is talking to. The mapping is
 * field-for-field from the core models the mirror stores; the two JSON-ish
 * fields the contract types as non-null `JsonObject` (device addresses,
 * capabilities, record metadata, command mutations) fall back to an empty
 * object when a row holds something else, which the remote JSON decoder
 * would have rejected at the door.
 *
 * Writes behave exactly like the Rust `SqliteStore`: `patchDevice` and
 * `revokeDevice` return the legacy `{"ok": true}` ack the callers already
 * ignore, `queueDeviceConfigCommand` lands a pending row the next sync round
 * pushes, and `record` answers 404 the way the remote endpoint does when the
 * id is unknown.
 */
class LocalMirrorClient(private val store: DesktopLocalStore) : ConsoleDataClient {

    override suspend fun systemInfo(): SystemInfoResponse {
        val info = store.getSystemInfo()
        return SystemInfoResponse(
            service = info.service,
            appEnv = info.appEnv,
            localBaseUrl = info.localBaseUrl,
            publicBaseUrl = info.publicBaseUrl,
            databaseReady = info.databaseReady,
            userCount = info.userCount,
            time = info.time,
        )
    }

    override suspend fun devices(): DevicesResponse =
        DevicesResponse(devices = store.listDevices().map { it.toItem() })

    override suspend fun deviceConfig(deviceId: Long): DeviceConfigStateResponse? =
        store.getDeviceConfig(deviceId).let { state ->
            // The concrete store fabricates an empty state for an unknown
            // device instead of answering null the way the 404 route does;
            // the UI reads revision 0 with an empty snapshot as "no config".
            DeviceConfigStateResponse(
                deviceId = state.deviceId,
                revision = state.revision,
                mirrorContent = state.snapshot.asJsonObjectOrNull(),
                pendingCommands = state.pendingCommands.map { it.toResponse() },
                updatedAt = state.updatedAt.orEmpty(),
            )
        }

    override suspend fun queueDeviceConfigCommand(
        deviceId: Long,
        request: DeviceConfigCommandRequest,
    ): DeviceConfigCommandResponse = store.queueDeviceConfigCommand(
        deviceId = deviceId,
        baseRevision = request.baseRevision,
        summary = request.summary,
        mutation = request.mutation,
    ).toResponse()

    override suspend fun deviceConfigAuditLogs(
        deviceId: Long,
        limit: Int,
        offset: Int,
    ): DeviceConfigAuditLogsResponse {
        val page = store.listDeviceConfigAuditLogs(deviceId, limit, offset)
        return DeviceConfigAuditLogsResponse(
            logs = page.items.map { it.toItem() },
            limit = page.limit,
            offset = page.offset,
        )
    }

    override suspend fun records(limit: Int, deviceId: Long?): RecordsResponse {
        val page = store.listRecords(limit, deviceId)
        return RecordsResponse(
            records = page.items.map { it.toItem() },
            limit = page.limit.toLong(),
            offset = page.offset.toLong(),
        )
    }

    override suspend fun record(id: Long): RelayRecord =
        store.getRecord(id)?.toItem() ?: throw ConsoleApiException(404, "record $id not found")

    override suspend fun patchDevice(deviceId: Long, request: PatchDeviceRequest): JsonObject {
        store.patchDevice(deviceId, request.displayName, request.enabled)
        return LOCAL_WRITE_ACK
    }

    override suspend fun revokeDevice(deviceId: Long): JsonObject {
        store.revokeDevice(deviceId)
        return LOCAL_WRITE_ACK
    }

    override suspend fun createBindCode(): BindCodeResponse =
        store.createBindCode().let { BindCodeResponse(code = it.code, expiresAt = it.expiresAt) }

    private fun Device.toItem(): DeviceItem = DeviceItem(
        id = id,
        userId = userId,
        deviceName = deviceName,
        deviceModel = deviceModel,
        platform = platform,
        appVersion = appVersion,
        displayName = displayName,
        enabled = enabled,
        revokedAt = revokedAt,
        lastSeenAt = lastSeenAt,
        localAddresses = localAddresses.asJsonObject(),
        capabilities = capabilities.asJsonObject(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Record.toItem(): RelayRecord = RelayRecord(
        id = id,
        deviceId = deviceId,
        eventId = eventId,
        recordType = recordType,
        sender = sender,
        body = body,
        smsCode = smsCode,
        packageName = packageName,
        msgType = msgType,
        callType = callType,
        occurredAt = occurredAt,
        uploadedAt = uploadedAt,
        metadata = metadata.asJsonObject(),
    )

    private fun DeviceConfigCommand.toResponse(): DeviceConfigCommandResponse =
        DeviceConfigCommandResponse(
            id = id,
            baseRevision = baseRevision,
            targetRevision = targetRevision,
            mutation = mutation.asJsonObject(),
            summary = summary,
            actorType = actorType,
            actorId = actorId,
            status = status,
            failureReason = failureReason,
            createdAt = createdAt,
            updatedAt = updatedAt,
            appliedAt = appliedAt,
        )

    private fun DeviceConfigAuditLog.toItem(): DeviceConfigAuditLogItem = DeviceConfigAuditLogItem(
        id = id,
        deviceId = deviceId,
        commandId = commandId,
        revision = revision,
        eventType = eventType,
        actorType = actorType,
        actorId = actorId,
        summary = summary,
        createdAt = createdAt,
    )

    private companion object {

        /** The Rust `SqliteStore` write ack (`sqlite_store.rs`), echoed verbatim. */
        val LOCAL_WRITE_ACK: JsonObject = JsonObject(mapOf("ok" to kotlinx.serialization.json.JsonPrimitive(true)))

        fun JsonElement.asJsonObjectOrNull(): JsonObject? = this as? JsonObject

        fun JsonElement.asJsonObject(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
    }
}
