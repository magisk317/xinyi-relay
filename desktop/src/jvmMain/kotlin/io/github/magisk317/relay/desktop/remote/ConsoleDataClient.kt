package io.github.magisk317.relay.desktop.remote

import io.github.magisk317.relay.contract.remote.BindCodeResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigAuditLogsResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandRequest
import io.github.magisk317.relay.contract.remote.DeviceConfigCommandResponse
import io.github.magisk317.relay.contract.remote.DeviceConfigStateResponse
import io.github.magisk317.relay.contract.remote.DevicesResponse
import io.github.magisk317.relay.contract.remote.PatchDeviceRequest
import io.github.magisk317.relay.contract.remote.RecordsResponse
import io.github.magisk317.relay.contract.remote.RelayRecord
import io.github.magisk317.relay.contract.remote.SystemInfoResponse
import kotlinx.serialization.json.JsonObject

/**
 * The console data surface every page reads and writes through.
 *
 * Two implementations answer it: the HTTP-backed [ConsoleClient] and the
 * mirror-backed `local.LocalMirrorClient`, which serves the same calls from
 * the SQLite store under the Local run mode. The split mirrors the Rust
 * desktop, where every data command short-circuits to the local store before
 * the remote client is touched when the run mode is Local (`main.rs`,
 * `if mode == RunMode::Local`).
 *
 * Methods that only make sense against a live backend — the browser-handoff
 * auth exchange, token refresh/logout, `me`, password change, admin
 * bootstrap — deliberately stay on [ConsoleClient] and are absent here: the
 * Rust shell keeps those remote in every run mode too.
 *
 * Callers must pass every argument explicitly: interface members cannot
 * declare default values, so the defaults the remote client used to carry
 * live on the concrete [ConsoleClient] only where no override exists.
 */
interface ConsoleDataClient {

    suspend fun systemInfo(): SystemInfoResponse

    suspend fun devices(): DevicesResponse

    /** Null when the device has no config yet, like the remote 404. */
    suspend fun deviceConfig(deviceId: Long): DeviceConfigStateResponse?

    /** Queues a config command and returns the persisted command (pending until the agent acks it). */
    suspend fun queueDeviceConfigCommand(
        deviceId: Long,
        request: DeviceConfigCommandRequest,
    ): DeviceConfigCommandResponse

    suspend fun deviceConfigAuditLogs(
        deviceId: Long,
        limit: Int,
        offset: Int,
    ): DeviceConfigAuditLogsResponse

    /** [deviceId] null means every device; [limit] caps the page. */
    suspend fun records(limit: Int, deviceId: Long?): RecordsResponse

    suspend fun record(id: Long): RelayRecord

    /** Rename/toggle a device; the response body is the legacy `{"ok": true}` ack. */
    suspend fun patchDevice(deviceId: Long, request: PatchDeviceRequest): JsonObject

    /** Revoke a device and its tokens; the response body is the legacy `{"ok": true}` ack. */
    suspend fun revokeDevice(deviceId: Long): JsonObject

    /** One-time bind code a local device redeems against this console. */
    suspend fun createBindCode(): BindCodeResponse
}
