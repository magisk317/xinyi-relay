package io.github.magisk317.relay.desktop.core.store

import io.github.magisk317.relay.desktop.core.model.Device
import io.github.magisk317.relay.desktop.core.model.DeviceConfigAuditLog
import io.github.magisk317.relay.desktop.core.model.DeviceConfigCommand
import io.github.magisk317.relay.desktop.core.model.DeviceConfigState
import io.github.magisk317.relay.desktop.core.model.Paginated
import io.github.magisk317.relay.desktop.core.model.Record
import io.github.magisk317.relay.desktop.core.model.RecordSyncResult
import io.github.magisk317.relay.desktop.core.model.SystemInfo
import kotlinx.serialization.json.JsonElement

/**
 * Error model of the desktop store.
 *
 * `Conflict` carries both revisions because the UI has to explain which side
 * won; the message format matches the Rust store so existing translations and
 * log greps keep working.
 */
sealed class StoreError(message: String?) : Throwable(message) {

    data class Internal(override val message: String?) : StoreError(message)

    data class Conflict(val local: Long, val remote: Long) :
        StoreError(conflictMessage(local, remote)) {
        override fun toString(): String = conflictMessage(local, remote)
    }

    companion object {
        fun conflictMessage(local: Long, remote: Long): String =
            "conflict: local revision $local vs remote revision $remote"
    }
}

/**
 * The local SQLite-backed store used by Local and Hybrid modes.
 *
 * Semantics are a one-for-one port of `sqlite_store.rs`; every divergence is
 * documented on the implementing method.
 */
interface Store {

    // ------------------------------------------------------------ config

    suspend fun getDeviceConfig(deviceId: Long): DeviceConfigState?

    suspend fun upsertDeviceConfigMirror(
        deviceId: Long,
        revision: Long,
        snapshot: JsonElement,
        updatedAt: String? = null,
    ): DeviceConfigState

    suspend fun queueDeviceConfigCommand(
        deviceId: Long,
        baseRevision: Long,
        summary: String,
        mutation: JsonElement,
    ): DeviceConfigCommand

    suspend fun listDeviceConfigAuditLogs(
        deviceId: Long,
        limit: Int,
        offset: Int,
    ): Paginated<DeviceConfigAuditLog>

    suspend fun replaceDeviceConfigPendingCommands(
        deviceId: Long,
        commands: List<DeviceConfigCommand>,
    )

    suspend fun clearDeviceConfigPendingCommands(deviceId: Long)

    // ------------------------------------------------------------ devices

    suspend fun listDevices(): List<Device>

    suspend fun patchDevice(
        deviceId: Long,
        displayName: String? = null,
        enabled: Boolean? = null,
    )

    suspend fun revokeDevice(deviceId: Long)

    suspend fun upsertDevices(devices: List<Device>)

    // ------------------------------------------------------------ records

    suspend fun listRecords(limit: Int, deviceId: Long? = null): Paginated<Record>

    suspend fun getRecord(recordId: Long): Record?

    suspend fun upsertRecords(records: List<Record>)

    // ------------------------------------------------------------ system

    suspend fun getSystemInfo(): SystemInfo
}

/**
 * Local-only operations that have no remote counterpart: these implement the
 * embedded agent HTTP API and are never forwarded to the backend.
 */
interface LocalDeviceRegistry {

    suspend fun createBindCode(): io.github.magisk317.relay.desktop.core.model.BindCode

    suspend fun registerLocalDevice(
        bindCode: String,
        deviceName: String,
        deviceModel: String,
        platform: String,
        appVersion: String,
        deviceToken: String,
    ): Device?

    suspend fun authenticateLocalDevice(token: String): Long?

    suspend fun updateLocalDeviceHeartbeat(
        deviceId: Long,
        appVersion: String,
        localAddresses: JsonElement,
        capabilities: JsonElement,
    )

    suspend fun ackLocalDeviceConfigCommand(
        deviceId: Long,
        commandId: Long,
        status: String,
        appliedRevision: Long,
        failureReason: String,
        snapshot: JsonElement,
    ): DeviceConfigCommand

    suspend fun syncLocalDeviceRecords(
        deviceId: Long,
        records: List<Record>,
        replaceExisting: Boolean,
    ): RecordSyncResult
}
