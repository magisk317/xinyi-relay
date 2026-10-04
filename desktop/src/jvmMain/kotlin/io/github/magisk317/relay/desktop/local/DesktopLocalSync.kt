package io.github.magisk317.relay.desktop.local

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.magisk317.relay.desktop.core.store.DesktopLocalStore
import io.github.magisk317.relay.desktop.core.store.RemoteStore
import io.github.magisk317.relay.desktop.core.model.BindCode
import io.github.magisk317.relay.desktop.core.model.Device
import io.github.magisk317.relay.desktop.core.model.DeviceConfigAuditLog
import io.github.magisk317.relay.desktop.core.model.DeviceConfigCommand
import io.github.magisk317.relay.desktop.core.model.DeviceConfigState
import io.github.magisk317.relay.desktop.core.model.Paginated
import io.github.magisk317.relay.desktop.core.model.Record
import io.github.magisk317.relay.desktop.core.model.SyncReport
import io.github.magisk317.relay.desktop.core.model.SystemInfo
import kotlinx.serialization.json.JsonElement
import java.net.InetSocketAddress
import kotlinx.coroutines.CancellationException
import io.github.magisk317.relay.desktop.data.DesktopDatabase

/**
 * Snapshot of the local mirror for the shell footer: the row counts the last
 * sync round fetched from the backend, plus whether a round is in flight or
 * has failed.
 *
 * Counts describe the sync traffic of the last round (the store is an upsert
 * mirror, so a round that fetched nothing reports zeros), not the total rows
 * held locally.
 */
data class LocalSyncStatus(
    val devices: Int = 0,
    val records: Int = 0,
    val syncing: Boolean = false,
    val error: String? = null,
) {
    /** True once a round has completed and touched at least one row. */
    val established: Boolean get() = error == null && (devices > 0 || records > 0)
}

/**
 * Owns the app's [DesktopLocalRuntime]: opens the SQLite mirror against the
 * active profile's backend, runs the first pull, keeps re-syncing on the
 * cadence the caller drives, and closes the database on stop.
 *
 * The runtime factory is injectable so tests open in-memory stores; the app
 * passes [DesktopLocalRuntime.create]. A failing round leaves the previous
 * counts in place and records the error — the next tick retries, the same way
 * the Rust client marks the connection disconnected and waits for the tick.
 */
class DesktopLocalSyncController(
    private val openRuntime: (RemoteStore) -> DesktopLocalRuntime =
        { remote -> DesktopLocalRuntime.create(remote) },
) {

    var status by mutableStateOf(LocalSyncStatus())
        private set

    private var runtime: DesktopLocalRuntime? = null
    private var localServer: DesktopLocalServer? = null

    /** Loopback URL advertised to the Android agent in Local mode. */
    var localServerUrl by mutableStateOf<String?>(null)
        private set

    /** True while the mirror is open. */
    val active: Boolean get() = runtime != null

    /**
     * The open mirror, or null while it is closed (Remote run mode, before
     * the first successful open). The database transfer controller reads
     * this to share the sync engine's connection.
     */
    val database: DesktopDatabase? get() = runtime?.database

    /**
     * The open mirror store — the data half of the runtime, read by the Local
     * run mode's read router. Same lifecycle as [database].
     */
    val store: DesktopLocalStore? get() = runtime?.store

    /**
     * Closes any previous runtime (profile switch), opens a fresh one against
     * [remote] and runs the forced first pull. A failure to open surfaces as an
     * error status instead of crashing the caller.
     */
    suspend fun start(remote: RemoteStore) {
        stop()
        status = LocalSyncStatus(syncing = true)
        val opened = try {
            openRuntime(remote)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            status = LocalSyncStatus(error = failure.message ?: "cannot open the local store")
            return
        }
        runtime = opened
        startLocalServer(opened.store)
        round(opened)
    }

    /**
     * Opens the persistent mirror without a backend session. Local mode can
     * serve existing data and the embedded agent server before login; no sync
     * round is attempted until a profile is authenticated.
     */
    suspend fun startLocalOnly() {
        stop()
        val opened = try {
            openRuntime(OFFLINE_REMOTE)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            status = LocalSyncStatus(error = failure.message ?: "cannot open the local store")
            return
        }
        runtime = opened
        startLocalServer(opened.store)
        status = LocalSyncStatus()
    }

    /**
     * One periodic round. No-ops when no runtime is open. The round is forced:
     * the controller's own tick is the cadence gate, and forcing keeps the
     * status counts tied to the round that just ran.
     */
    suspend fun sync() {
        val opened = runtime ?: return
        round(opened)
    }

    /**
     * Sends the mirror's queued device-config commands to the backend, the
     * other half of [sync]. Config edits in the Local and Hybrid run modes
     * land in the mirror's pending queue and only leave the machine here, so
     * the advanced page exposes this direction as its "push to remote" button.
     *
     * Returns null while no runtime is open, and rethrows a failing round so
     * the caller can surface it; a successful round reports through
     * [lastReport]. Errors are deliberately not folded into [status]: the
     * mirror is still open and readable, so a failed push must not blank the
     * footer read-out the way a failed mirror open does.
     */
    suspend fun push(): SyncReport? {
        val opened = runtime ?: return null
        val report = opened.sync.push()
        lastReport = report
        return report
    }

    /**
     * The outcome of the most recent caller-driven [sync] or [push], for the
     * advanced page's result card. Null until one runs.
     */
    var lastReport by mutableStateOf<SyncReport?>(null)
        private set

    /** Closes the database and resets the status. Idempotent. */
    fun stop() {
        localServer?.close()
        localServer = null
        localServerUrl = null
        runtime?.close()
        runtime = null
        status = LocalSyncStatus()
    }

    private suspend fun round(opened: DesktopLocalRuntime) {
        status = status.copy(syncing = true, error = null)
        val report = try {
            opened.sync.initialPull()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            status = status.copy(syncing = false, error = failure.message ?: "sync failed")
            return
        }
        status = LocalSyncStatus(
            devices = report.devicesSynced,
            records = report.recordsSynced,
        )
        lastReport = report
    }

    private fun startLocalServer(store: DesktopLocalStore) {
        val next = DesktopLocalServer(store)
        val address = runCatching { next.start() }.getOrNull()
        if (address == null) {
            next.close()
            return
        }
        localServer = next
        localServerUrl = loopbackUrl(address)
    }

    private fun loopbackUrl(address: InetSocketAddress): String =
        "http://${address.hostString}:${address.port}"

    private companion object {
        /** A local-only runtime must never issue network requests. */
        val OFFLINE_REMOTE: RemoteStore = object : RemoteStore {
            override suspend fun getDeviceConfig(deviceId: Long): DeviceConfigState? = offline()
            override suspend fun queueDeviceConfigCommand(
                deviceId: Long,
                baseRevision: Long,
                summary: String,
                mutation: JsonElement,
            ): DeviceConfigCommand = offline()
            override suspend fun listDeviceConfigAuditLogs(
                deviceId: Long,
                limit: Int,
                offset: Int,
            ): Paginated<DeviceConfigAuditLog> = offline()
            override suspend fun listDevices(): List<Device> = offline()
            override suspend fun patchDevice(
                deviceId: Long,
                displayName: String?,
                enabled: Boolean?,
            ): JsonElement = offline()
            override suspend fun revokeDevice(deviceId: Long): JsonElement = offline()
            override suspend fun createBindCode(): BindCode = offline()
            override suspend fun listRecords(limit: Int, deviceId: Long?): Paginated<Record> = offline()
            override suspend fun getRecord(recordId: Long): Record? = offline()
            override suspend fun getSystemInfo(): SystemInfo = offline()
            override fun setAccessToken(token: String?) = Unit

            private fun <T> offline(): T = error("backend unavailable in local-only mode")
        }
    }
}
