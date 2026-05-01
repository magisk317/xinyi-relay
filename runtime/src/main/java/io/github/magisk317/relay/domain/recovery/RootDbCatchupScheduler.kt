package io.github.magisk317.relay.domain.recovery

import android.content.Context
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object RootDbCatchupScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var periodicJob: Job? = null

    fun startPeriodic(context: Context, reason: String) {
        val appContext = context.applicationContext ?: context
        if (periodicJob?.isActive == true) {
            return
        }
        periodicJob = scope.launch {
            XLog.i("Root DB catchup periodic started reason=%s", reason)
            while (isActive) {
                RootDbCatchupEngine.runOnce(appContext, reason = "periodic:$reason")
                val runtimeGraph = RuntimeGraph.from(appContext)
                val intervalMin = RuntimeSettingsCache.getString(
                    key = PrefConst.KEY_ROOT_DB_CATCHUP_INTERVAL_MIN,
                    defaultValue = "5",
                ) { key, defaultValue ->
                    runtimeGraph.preferenceDataSource.getString(key, defaultValue)
                }.toLongOrNull() ?: 5L
                val intervalMs = intervalMin.coerceAtLeast(1L) * 60_000L
                delay(intervalMs)
            }
        }
    }

    fun stopPeriodic(reason: String) {
        val job = periodicJob ?: return
        job.cancel()
        periodicJob = null
        XLog.i("Root DB catchup periodic stopped reason=%s", reason)
    }

    fun triggerImmediate(context: Context, reason: String) {
        val appContext = context.applicationContext ?: context
        scope.launch {
            RootDbCatchupEngine.runOnce(appContext, reason = reason)
        }
    }
}
