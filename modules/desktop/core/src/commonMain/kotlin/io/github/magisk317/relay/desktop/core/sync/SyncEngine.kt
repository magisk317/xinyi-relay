package io.github.magisk317.relay.desktop.core.sync

import io.github.magisk317.relay.desktop.core.model.Device
import io.github.magisk317.relay.desktop.core.model.DeviceConfigCommand
import io.github.magisk317.relay.desktop.core.model.DeviceConfigState
import io.github.magisk317.relay.desktop.core.model.Paginated
import io.github.magisk317.relay.desktop.core.model.Record
import io.github.magisk317.relay.desktop.core.model.RunMode
import io.github.magisk317.relay.desktop.core.model.SyncReport
import io.github.magisk317.relay.desktop.core.model.SyncResult
import io.github.magisk317.relay.desktop.core.store.Store
import io.github.magisk317.relay.desktop.core.store.StoreError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

/**
 * Kotlin port of the Rust `sync.rs` engine.
 *
 * Two properties of the original are preserved deliberately:
 *
 * - **Single flight**: a second sync while one is running raises the
 *   `Sync already in progress` error rather than queueing.
 * - **No retry/backoff**: a transport failure surfaces immediately and the
 *   caller marks the connection as disconnected; the periodic tick retries.
 *
 * The record pull is still capped by [recordLimit] without a cursor, exactly as
 * in the Rust store. Widening it is tracked separately because it changes how
 * much data a single tick moves.
 */
class SyncEngine(
    private val local: Store,
    private val remote: RemoteView,
    private val recordLimit: Int = DEFAULT_RECORD_LIMIT,
    private val throttler: SyncThrottler = SyncThrottler(),
) {

    /**
     * Minimal remote surface the engine needs; both the HTTP-backed remote store
     * and a fake remote in tests satisfy it.
     */
    interface RemoteView {
        suspend fun listDevices(): List<Device>
        suspend fun getDeviceConfig(deviceId: Long): DeviceConfigState?
        suspend fun listRecords(limit: Int, deviceId: Long?): Paginated<Record>
        suspend fun queueDeviceConfigCommand(
            deviceId: Long,
            baseRevision: Long,
            summary: String,
            mutation: JsonElement,
        ): DeviceConfigCommand
    }

    private val guard = Mutex()

    suspend fun pull(
        mode: RunMode = RunMode.Hybrid,
        nowMillis: Long = throttler.nowMillis(),
        force: Boolean = false,
    ): SyncReport {
        if (!force && !throttler.canPull(mode, nowMillis)) {
            return SyncReport(config = SyncResult.UpToDate)
        }
        return withSingleFlight {
            throttler.notePull(mode, nowMillis)

            val remoteDevices = remote.listDevices()
            if (remoteDevices.isNotEmpty()) {
                local.upsertDevices(remoteDevices)
            }

            var pulledRevision: Long? = null
            var conflict: Pair<Long, Long>? = null

            remoteDevices.forEach { device ->
                val remoteState = remote.getDeviceConfig(device.id) ?: return@forEach
                val localState = local.getDeviceConfig(device.id)
                val localRevision = localState?.revision ?: 0L
                val localHasPending = localState?.pendingCommands.orEmpty().isNotEmpty()

                when {
                    remoteState.revision > localRevision -> {
                        local.upsertDeviceConfigMirror(
                            deviceId = device.id,
                            revision = remoteState.revision,
                            snapshot = remoteState.snapshot,
                            updatedAt = remoteState.updatedAt,
                        )
                        if (!localHasPending) {
                            local.replaceDeviceConfigPendingCommands(
                                device.id,
                                remoteState.pendingCommands,
                            )
                        }
                        pulledRevision = maxOf(pulledRevision ?: remoteState.revision, remoteState.revision)
                    }

                    localRevision > remoteState.revision && localHasPending -> {
                        conflict = localRevision to remoteState.revision
                    }

                    else -> {
                        if (!localHasPending) {
                            local.replaceDeviceConfigPendingCommands(
                                device.id,
                                remoteState.pendingCommands,
                            )
                        }
                        if (remoteState.revision >= localRevision) {
                            pulledRevision = maxOf(pulledRevision ?: remoteState.revision, remoteState.revision)
                        }
                    }
                }
            }

            val remoteRecords = remote.listRecords(recordLimit, null)
            if (remoteRecords.items.isNotEmpty()) {
                local.upsertRecords(remoteRecords.items)
            }

            val result = when {
                conflict != null -> SyncResult.Conflict(
                    localRevision = conflict!!.first,
                    remoteRevision = conflict!!.second,
                )

                pulledRevision != null -> SyncResult.Pulled(pulledRevision!!)

                else -> SyncResult.UpToDate
            }
            SyncReport(
                config = result,
                devicesSynced = remoteDevices.size,
                recordsSynced = remoteRecords.items.size,
            )
        }
    }

    suspend fun push(): SyncReport = withSingleFlight {
        var pushedRevision: Long? = null
        var conflict: Pair<Long, Long>? = null

        local.listDevices().forEach { device ->
            val localState = local.getDeviceConfig(device.id) ?: return@forEach
            if (localState.pendingCommands.isEmpty()) return@forEach

            var remoteRevision = remote.getDeviceConfig(device.id)?.revision ?: 0L
            var pushedAll = true

            for (command in localState.pendingCommands) {
                if (command.baseRevision != remoteRevision) {
                    conflict = command.baseRevision to remoteRevision
                    pushedAll = false
                    break
                }
                val queued = try {
                    remote.queueDeviceConfigCommand(
                        deviceId = device.id,
                        baseRevision = command.baseRevision,
                        summary = command.summary,
                        mutation = command.mutation,
                    )
                } catch (error: StoreError.Conflict) {
                    conflict = command.baseRevision to remoteRevision
                    pushedAll = false
                    break
                }
                pushedRevision = pushedRevision
                    ?.let { maxOf(it, queued.targetRevision) }
                    ?: queued.targetRevision
                remoteRevision = queued.targetRevision
            }

            if (pushedAll) {
                local.clearDeviceConfigPendingCommands(device.id)
            }
        }

        val result = when {
            conflict != null -> SyncResult.Conflict(
                localRevision = conflict!!.first,
                remoteRevision = conflict!!.second,
            )

            pushedRevision != null -> SyncResult.Pushed(pushedRevision!!)

            else -> SyncResult.UpToDate
        }
        SyncReport(config = result)
    }

    suspend fun initialPull(mode: RunMode = RunMode.Hybrid): SyncReport =
        pull(mode = mode, force = true)

    private suspend fun withSingleFlight(block: suspend () -> SyncReport): SyncReport {
        // A `Mutex` acquired with `withLock` would queue a second concurrent sync
        // behind the first; the contract is to reject it. `tryLock` returns false
        // immediately when another sync holds the guard, so the second caller is
        // rejected with `Sync already in progress` instead of being queued.
        if (!guard.tryLock()) {
            throw StoreError.Internal(SYNC_IN_PROGRESS)
        }
        try {
            return block()
        } finally {
            guard.unlock()
        }
    }

    companion object {
        const val DEFAULT_RECORD_LIMIT = 100
        const val SYNC_IN_PROGRESS = "Sync already in progress"
    }
}

/**
 * Hybrid mode throttles automatic pulls to one every five minutes, matching the
 * Rust `last_auto_sync` gate. Remote-only mode is never throttled because it has
 * no local store to protect.
 */
class SyncThrottler(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val clock: () -> Long = ::systemNowMillis,
) {

    private val lastPull = mutableMapOf<RunMode, Long>()

    fun canPull(mode: RunMode, nowMillis: Long = clock()): Boolean {
        if (mode == RunMode.Remote) return true
        val previous = lastPull[mode] ?: return true
        return nowMillis - previous >= intervalMillis
    }

    fun notePull(mode: RunMode, nowMillis: Long = clock()) {
        lastPull[mode] = nowMillis
    }

    fun nowMillis(): Long = clock()

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 300_000L
    }
}

/**
 * Wall-clock source for the throttler. JVM-backed targets use the system clock;
 * tests inject their own.
 */
internal expect fun systemNowMillis(): Long
