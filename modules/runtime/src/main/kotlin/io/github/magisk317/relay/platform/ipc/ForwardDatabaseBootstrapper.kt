package io.github.magisk317.relay.platform.ipc

import android.content.Context
import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.forward.db.ForwardMetaEntity
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.ForwardLegacyImporter
import io.github.magisk317.xposed.logging.XLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opens the forward database on app start and fills it from the legacy mixed
 * schema. Event history is imported only when the forward event table is
 * still empty; every configuration table is imported independently while its
 * forward counterpart is empty, so a partial or interrupted import can be
 * retried on the next launch without overwriting newer writes.
 *
 * Readers call [awaitReady] before touching forward tables so the first read
 * after an upgrade cannot observe a half-filled store. The import outcome is
 * recorded in `forward_meta` (`ok` / `failed`) and failures stay retryable.
 */
object ForwardDatabaseBootstrapper {

    private const val KEY_CONFIG_IMPORT = "legacy_config_import"
    private const val STATE_OK = "ok"
    private const val STATE_FAILED = "failed"

    private val started = AtomicBoolean(false)
    private val ready = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun scheduleLegacyImport(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext ?: context
        scope.launch {
            try {
                runCatching {
                    val legacy = AppDatabase.getInstance(appContext)
                    val target = ForwardDatabase.build(appContext)
                    importEventsIfNeeded(legacy, target)
                    importConfigIfNeeded(legacy, target)
                }.exceptionOrNull()?.let { error ->
                    if (error is CancellationException) throw error
                    XLog.e(message = "Forward legacy import failed", error)
                }
            } finally {
                ready.complete(Unit)
            }
        }
    }

    suspend fun awaitReady() {
        if (!started.get()) return
        ready.await()
    }

    private suspend fun importEventsIfNeeded(legacy: AppDatabase, target: ForwardDatabase) {
        if (target.forwardEventDao().count() > 0) return
        if (legacy.smsMsgDao().getAll().isEmpty()) return
        val report = ForwardLegacyImporter.importAll(legacy, target)
        if (report.parity.ok) {
            XLog.w(
                message = "Forward import finished events=%d logs=%d parityOk=true",
                report.eventsImported,
                report.dispatchLogsImported,
            )
        } else {
            XLog.e(
                message = "Forward import parity failed events=%d logs=%d detail=%s",
                report.eventsImported,
                report.dispatchLogsImported,
                report.parity.firstEventMismatch ?: report.parity.firstDispatchLogMismatch ?: "<none>",
            )
        }
    }

    private suspend fun importConfigIfNeeded(legacy: AppDatabase, target: ForwardDatabase) {
        if (target.forwardMetaDao().get(KEY_CONFIG_IMPORT) == STATE_OK) {
            return
        }
        val report = ForwardLegacyImporter.importConfigIfNeeded(legacy, target)
        target.forwardMetaDao().put(
            ForwardMetaEntity(KEY_CONFIG_IMPORT, if (report.ok) STATE_OK else STATE_FAILED),
        )
        if (report.ok) {
            XLog.w(message = "Forward config import %s", report.summary())
        } else {
            XLog.e(
                message = "Forward config import incomplete, retrying next launch: %s",
                report.summary(),
            )
        }
    }
}
