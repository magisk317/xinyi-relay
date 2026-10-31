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

    private const val LOG_TAG = "LegacyMigration"
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
            val result = runCatching {
                sourceFactory(appContext).use { source ->
                    if (!source.legacyFileExists()) {
                        XLog.w(message = "$LOG_TAG: no legacy relay_room.db; fresh install, nothing to import")
                        return@launch
                    }
                    val target = ForwardDatabase.build(appContext)
                    target.withTransaction { importLegacyData(source, target) }
                }
            }
            val error = result.exceptionOrNull()
            when {
                error == null -> ready.complete(Unit)
                error is CancellationException -> {
                    ready.completeExceptionally(error)
                    throw error
                }
                else -> {
                    XLog.e(message = "Forward legacy import failed", error)
                    ready.completeExceptionally(error)
                }
            }
        }
    }

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
            runCatching { source.dropLegacyTables() }
                .onFailure { XLog.w(message = "$LOG_TAG: legacy table drop failed (cosmetic): %s", it.message) }
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

    suspend fun awaitReady() {
        if (!started.get()) return
        ready.await()
    }

    private suspend fun importEventsIfNeeded(source: LegacyMigrationSource, target: ForwardDatabase) {
        if (target.forwardEventDao().count() > 0) {
            XLog.w(message = "$LOG_TAG: forward_event already populated; event import skipped")
            return
        }
        val msgs = source.readSmsMsgs()
        if (msgs.isEmpty()) {
            XLog.w(message = "$LOG_TAG: legacy sms_msg empty or absent; event import skipped")
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
            XLog.w(message = "$LOG_TAG: config import already marked ok; skipped")
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
