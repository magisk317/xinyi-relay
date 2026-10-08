package io.github.magisk317.relay.desktop.core.store

import io.github.magisk317.relay.desktop.core.model.BindCode
import io.github.magisk317.relay.desktop.core.model.Device
import io.github.magisk317.relay.desktop.core.model.DeviceConfigAuditLog
import io.github.magisk317.relay.desktop.core.model.DeviceConfigCommand
import io.github.magisk317.relay.desktop.core.model.DeviceConfigState
import io.github.magisk317.relay.desktop.core.model.Paginated
import io.github.magisk317.relay.desktop.core.model.Record
import io.github.magisk317.relay.desktop.core.model.SystemInfo
import kotlinx.serialization.json.JsonElement

/**
 * Read-mostly view of the Go backend.
 *
 * `upsertDevices` / `upsertRecords` are deliberately absent: the desktop client
 * is a consumer for those tables, they are authoritative on the backend and are
 * pushed by the Android devices.
 */
interface RemoteStore {

    suspend fun getDeviceConfig(deviceId: Long): DeviceConfigState?

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

    suspend fun listDevices(): List<Device>

    suspend fun patchDevice(
        deviceId: Long,
        displayName: String? = null,
        enabled: Boolean? = null,
    ): JsonElement

    suspend fun revokeDevice(deviceId: Long): JsonElement

    suspend fun createBindCode(): BindCode

    suspend fun listRecords(limit: Int, deviceId: Long? = null): Paginated<Record>

    suspend fun getRecord(recordId: Long): Record?

    suspend fun getSystemInfo(): SystemInfo

    /**
     * Replaces the bearer token used for authenticated calls.
     */
    fun setAccessToken(token: String?)
}
