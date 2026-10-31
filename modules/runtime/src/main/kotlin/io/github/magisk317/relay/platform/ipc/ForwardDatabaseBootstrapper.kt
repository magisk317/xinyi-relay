package io.github.magisk317.relay.platform.ipc

import android.content.Context
import androidx.room.withTransaction
import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.forward.db.ForwardMetaEntity
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.ForwardLegacyImporter
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
 */
object ForwardDatabaseBootstrapper {

    private const val KEY_EVENT_IMPORT = "legacy_event_import"
    private const val KEY_CONFIG_IMPORT = "legacy_config_import"
    private const val KEY_IMPORT_FAILURE = "legacy_import_failure"
    private const val STATE_OK = "ok"
    private const val STATE_FAILED = "failed"

    private val started = AtomicBoolean(false)
    private val ready = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun scheduleLegacyImport(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext ?: context
        scope.launch {
            val result = runCatching {
                val legacy = AppDatabase.getInstance(appContext)
                val target = ForwardDatabase.build(appContext)
                target.withTransaction { importLegacyData(legacy, target) }
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
     * awaiting the gate never observes a partially populated store.
     */
    internal suspend fun importLegacyData(legacy: AppDatabase, target: ForwardDatabase) {
        var failedKey = KEY_CONFIG_IMPORT
        val result = runCatching {
            failedKey = KEY_EVENT_IMPORT
            importEventsIfNeeded(legacy, target)
            failedKey = KEY_CONFIG_IMPORT
            importConfigIfNeeded(legacy, target)
            target.forwardMetaDao().remove(KEY_IMPORT_FAILURE)
            // Mark the legacy source drainable. The next schema bump drops the
            // legacy tables; that migration reads this marker, so a device that
            // never completed the import keeps its source instead of losing it.
            legacy.openHelper.writableDatabase.execSQL(
                "CREATE TABLE IF NOT EXISTS legacy_import_done (proceed INTEGER NOT NULL)",
            )
            legacy.openHelper.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO legacy_import_done (proceed) VALUES (1)",
            )
        }
        val error = result.exceptionOrNull() ?: return
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

    private suspend fun importEventsIfNeeded(legacy: AppDatabase, target: ForwardDatabase) {
        if (target.forwardEventDao().count() > 0) return
        if (legacy.smsMsgDao().getAll().isEmpty()) return
        val report = ForwardLegacyImporter.importAll(legacy, target)
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

    private suspend fun importConfigIfNeeded(legacy: AppDatabase, target: ForwardDatabase) {
        if (target.forwardMetaDao().get(KEY_CONFIG_IMPORT) == STATE_OK) return
        val report = ForwardLegacyImporter.importConfigIfNeeded(legacy, target)
        if (!report.ok) {
            XLog.e(message = "Forward config import incomplete, rolling back: %s", report.summary())
            error("Forward config import parity failed: ${report.summary()}")
        }
        target.forwardMetaDao().put(ForwardMetaEntity(KEY_CONFIG_IMPORT, STATE_OK))
        XLog.w(message = "Forward config import %s", report.summary())
    }
}
