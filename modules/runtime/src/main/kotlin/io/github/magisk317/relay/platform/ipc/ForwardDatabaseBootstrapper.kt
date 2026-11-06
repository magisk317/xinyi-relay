package io.github.magisk317.relay.platform.ipc

import android.content.Context
import androidx.room.withTransaction
import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.forward.db.ForwardMetaEntity
import io.github.magisk317.relay.android.data.db.ForwardLegacyImporter
import io.github.magisk317.relay.android.data.db.legacy.LegacyMigrationSource
import io.github.magisk317.relay.android.data.db.legacy.LegacyRawSource
import io.github.magisk317.xposed.logging.XLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opens the forward database on app start and atomically imports the legacy
 * mixed schema. Readers wait for this gate; a parity or I/O failure makes the
 * gate fail closed instead of exposing a partially populated forward store.
 *
 * Since v34 the legacy reads run through raw SQLite
 * ([LegacyRawSource]) instead of Room, so the v34 schema could drop the
 * legacy entity types. After a fully successful import the source is marked
 * drained and its legacy tables are dropped (best-effort cosmetic); a failed
 * import keeps the source tables intact for a retry.
 */
object ForwardDatabaseBootstrapper {

    /** Upper bound for a single reader waiting on the readiness gate. */
    private const val READY_WAIT_LIMIT_MS = 30_000L

    private const val KEY_EVENT_IMPORT = "legacy_event_import"
    private const val KEY_CONFIG_IMPORT = "legacy_config_import"
    private const val KEY_IMPORT_FAILURE = "legacy_import_failure"
    private const val STATE_OK = "ok"
    private const val STATE_FAILED = "failed"

    private val started = AtomicBoolean(false)
    private val ready = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun scheduleLegacyImport(
        context: Context,
        sourceFactory: (Context) -> LegacyMigrationSource = ::LegacyRawSource,
    ) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext ?: context
        scope.launch {
            drainAndRelease(sourceFactory(appContext)) { ForwardDatabase.build(appContext) }
        }
    }

    /**
     * Drains the legacy source and releases the readiness gate exactly once - including
     * when there is nothing to drain.
     *
     * A fresh install (and every install that follows an uninstall) has no legacy database
     * at all. Returning early from the launch block without releasing the gate used to leave
     * [ready] uncompleted forever, so every reader that awaits it - the forward-domain
     * repositories and the runtime diagnostics page behind them - suspended indefinitely.
     * Split out of the launch block so the release is testable without an Android context.
     *
     * Returns whether the gate ended up released normally (an import failure completes it
     * exceptionally and readers keep failing closed).
     */
    internal suspend fun drainAndRelease(
        source: LegacyMigrationSource,
        targetFactory: () -> ForwardDatabase,
    ): Boolean {
        val result = runCatching {
            source.use { legacy ->
                if (!legacy.legacyFileExists()) {
                    XLog.i(message = "Forward legacy import skipped: no legacy database, gate released")
                    return@use
                }
                val target = targetFactory()
                target.withTransaction { importLegacyData(legacy, target) }
            }
        }
        val error = result.exceptionOrNull()
        return when {
            error == null -> {
                ready.complete(Unit)
                true
            }
            error is CancellationException -> {
                ready.completeExceptionally(error)
                throw error
            }
            else -> {
                XLog.e(message = "Forward legacy import failed", error)
                ready.completeExceptionally(error)
                false
            }
        }
    }

    /** Whether readers can already get past [awaitReady]; exposed for the readiness tests. */
    internal fun gateIsReady(): Boolean = ready.isCompleted

    /**
     * Imports the legacy mixed schema into [target] and fails closed: a parity or I/O
     * failure records the stage that broke in forward_meta and rethrows, so anything
     * awaiting the gate never observes a partially populated store. On full success
     * the source is marked drained and its legacy tables are dropped.
     */
    internal suspend fun importLegacyData(source: LegacyMigrationSource, target: ForwardDatabase) {
        var failedKey = KEY_CONFIG_IMPORT
        val result = runCatching {
            failedKey = KEY_EVENT_IMPORT
            importEventsIfNeeded(source, target)
            failedKey = KEY_CONFIG_IMPORT
            importConfigIfNeeded(source, target)
            target.forwardMetaDao().remove(KEY_IMPORT_FAILURE)
            source.markDrained()
        }
        val error = result.exceptionOrNull()
        if (error == null) {
            // The import fully succeeded, so the legacy tables are drained.
            // Dropping is best-effort cosmetic: a failure here leaves dead
            // tables behind, it can never lose data.
            try {
                source.dropLegacyTables()
            } catch (_: Throwable) {
                // Best effort only; retained legacy tables cannot invalidate imported data.
            }
            return
        }
        if (error is CancellationException) throw error
        runCatching {
            target.forwardMetaDao().put(ForwardMetaEntity(failedKey, STATE_FAILED))
            target.forwardMetaDao().put(ForwardMetaEntity(KEY_IMPORT_FAILURE, error.javaClass.simpleName))
        }.onFailure { markerError ->
            XLog.e(message = "Failed to record forward import failure", markerError)
        }
        throw error
    }

    /**
     * Bridges the import window only. A gate that is still closed after the limit means the
     * import itself is wedged, and hanging every reader behind it turns that into an
     * unresponsive UI; the limit therefore releases readers with a loud warning. A gate that
     * completed exceptionally still throws - failures keep failing closed.
     */
    suspend fun awaitReady() {
        if (!started.get()) return
        val released = withTimeoutOrNull(READY_WAIT_LIMIT_MS) {
            ready.await()
            true
        } == true
        if (!released) {
            XLog.w(message = "Forward readiness gate still closed after ${READY_WAIT_LIMIT_MS}ms; continuing unlocked")
        }
    }

    private suspend fun importEventsIfNeeded(source: LegacyMigrationSource, target: ForwardDatabase) {
        if (target.forwardEventDao().count() > 0) {
            return
        }
        val msgs = source.readSmsMsgs()
        if (msgs.isEmpty()) {
            return
        }
        val logs = source.readDispatchLogs()
        val report = ForwardLegacyImporter.importAll(msgs, logs, target)
        if (!report.parity.ok) {
            val detail = report.parity.firstEventMismatch
                ?: report.parity.firstDispatchLogMismatch
                ?: "unknown parity mismatch"
            XLog.e(
                message = "Forward import parity failed events=%d logs=%d detail=%s",
                report.eventsImported,
                report.dispatchLogsImported,
                detail,
            )
            error("Forward event import parity failed: $detail")
        }
        XLog.w(
            message = "Forward import finished events=%d logs=%d parityOk=true",
            report.eventsImported,
            report.dispatchLogsImported,
        )
    }

    private suspend fun importConfigIfNeeded(source: LegacyMigrationSource, target: ForwardDatabase) {
        if (target.forwardMetaDao().get(KEY_CONFIG_IMPORT) == STATE_OK) {
            return
        }
        val report = ForwardLegacyImporter.importConfigIfNeeded(source.readConfig(), target)
        if (!report.ok) {
            XLog.e(message = "Forward config import incomplete, rolling back: %s", report.summary())
            error("Forward config import parity failed: ${report.summary()}")
        }
        target.forwardMetaDao().put(ForwardMetaEntity(KEY_CONFIG_IMPORT, STATE_OK))
        XLog.w(message = "Forward config import %s", report.summary())
    }
}
