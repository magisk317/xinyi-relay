package io.github.magisk317.relay.platform.ipc

import android.content.Context
import io.github.magisk317.forward.db.ForwardDatabase
import io.github.magisk317.relay.android.data.db.AppDatabase
import io.github.magisk317.relay.android.data.db.ForwardLegacyImporter
import io.github.magisk317.xposed.logging.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

/**
 * Opens the forward database on app start and fills it from the legacy mixed
 * schema exactly once: the import runs when the forward tables are empty and
 * the legacy tables are not, and parity is enforced before anything else
 * reads the new store.
 */
object ForwardDatabaseBootstrapper {

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun scheduleLegacyImport(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext ?: context
        scope.launch {
            runCatching {
                val legacy = AppDatabase.getInstance(appContext)
                val target = ForwardDatabase.build(appContext)
                if (target.forwardEventDao().count() > 0) return@runCatching
                if (legacy.smsMsgDao().getAll().isEmpty()) return@runCatching
                val report = ForwardLegacyImporter.importAll(legacy, target)
                val configResult = ForwardLegacyImporter.importConfig(legacy, target)
                XLog.w(
                    message = "Forward import finished events=%d logs=%d parityOk=%s detail=%s config=%s",
                    report.eventsImported,
                    report.dispatchLogsImported,
                    report.parity.ok,
                    report.parity.firstEventMismatch ?: report.parity.firstDispatchLogMismatch ?: "<none>",
                    configResult,
                )
            }.onFailure { error ->
                XLog.e(message = "Forward legacy import failed", error)
            }
        }
    }
}
