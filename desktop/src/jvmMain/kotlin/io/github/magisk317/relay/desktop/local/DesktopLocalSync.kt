package io.github.magisk317.relay.desktop.local

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.magisk317.relay.desktop.core.store.DesktopLocalStore
import io.github.magisk317.relay.desktop.core.store.RemoteStore
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
        } catch (failure: Exception) {
            status = LocalSyncStatus(error = failure.message ?: "cannot open the local store")
            return
        }
        runtime = opened
        round(opened, force = true)
    }

    /**
     * One periodic round. No-ops when no runtime is open. The round is forced:
     * the controller's own tick is the cadence gate, and forcing keeps the
     * status counts tied to the round that just ran.
     */
    suspend fun sync() {
        val opened = runtime ?: return
        round(opened, force = true)
    }

    /** Closes the database and resets the status. Idempotent. */
    fun stop() {
        runtime?.close()
        runtime = null
        status = LocalSyncStatus()
    }

    private suspend fun round(opened: DesktopLocalRuntime, force: Boolean) {
        status = status.copy(syncing = true, error = null)
        val report = try {
            if (force) opened.sync.initialPull() else opened.sync.pull()
        } catch (failure: Exception) {
            status = status.copy(syncing = false, error = failure.message ?: "sync failed")
            return
        }
        status = LocalSyncStatus(
            devices = report.devicesSynced,
            records = report.recordsSynced,
        )
    }
}
