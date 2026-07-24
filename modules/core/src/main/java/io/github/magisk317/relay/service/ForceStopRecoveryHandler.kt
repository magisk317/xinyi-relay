package io.github.magisk317.relay.service

import io.github.magisk317.relay.android.diagnostics.RuntimeDiagnosticsBridge
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.domain.recovery.RootDbCatchupScheduler
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.relay.security.IpcTokenGate
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.github.magisk317.xposed.logging.MagiskOtel

object ForceStopRecoveryHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wakeupThrottle = ProcessEventThrottle(
        minIntervalMillis = WAKEUP_MIN_INTERVAL_MILLIS,
        elapsedRealtime = SystemClock::elapsedRealtime,
    )

    fun handle(context: Context, intent: Intent, tag: String) {
        if (!wakeupThrottle.tryAcquire()) {
            XLog.w(LogRoute.ROOT_DB, "ForceStopRecoveryService wakeup throttled")
            MagiskOtel.event(
                name = "app.recovery",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "force_stop",
                    "reason" to "throttled",
                    "source" to tag,
                ),
                statusOk = true,
            )
            return
        }
        scope.launch {
            handleInternal(context, intent, tag)
        }
    }

    private suspend fun handleInternal(context: Context, intent: Intent, tag: String) {
        val expectedToken = loadExpectedToken(context)
        val receivedToken = intent.getStringExtra(ForceStopRecoveryContract.EXTRA_IPC_TOKEN)
        val tokenAccepted = IpcTokenGate.isAccepted(
            expectedToken = expectedToken,
            receivedToken = receivedToken,
        )
        if (!tokenAccepted) {
            XLog.w(
                LogRoute.ROOT_DB,
                "ForceStopRecoveryService rejected token. expectedEmpty=%s receivedEmpty=%s",
                expectedToken.isBlank(),
                receivedToken.isNullOrBlank(),
            )
            MagiskOtel.event(
                name = "app.recovery",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "app",
                    "stage" to "force_stop",
                    "reason" to "token_rejected",
                    "source" to tag,
                ),
                statusOk = false,
            )
            return
        }
        val reason = intent.getStringExtra(ForceStopRecoveryContract.EXTRA_REASON).orEmpty()
        val eventId = intent.getStringExtra(ForceStopRecoveryContract.EXTRA_EVENT_ID).orEmpty()
        XLog.w(
            LogRoute.ROOT_DB,
            "ForceStopRecoveryService started. reason=%s event=%s",
            reason.ifBlank { "<none>" },
            eventId.ifBlank { "<none>" },
        )
        RuntimeDiagnosticsBridge.ensureInstalled()
        RuntimeLogStore.append(
            Log.WARN,
            tag,
            "force-stop recovery wakeup reason=${reason.ifBlank { "<none>" }} event=${eventId.ifBlank { "<none>" }}",
            force = true,
            route = RuntimeLogStore.ROUTE_ROOT_DB,
        )
        RootDbCatchupScheduler.triggerImmediate(
            context = context,
            reason = "force_stop_recovery",
        )
        MagiskOtel.event(
            name = "app.recovery",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "force_stop",
                "reason" to reason.ifBlank { "force_stop_recovery" },
                "source" to tag,
                "event_id_present" to eventId.isNotBlank().toString(),
            ),
            statusOk = true,
        )
    }

    private suspend fun loadExpectedToken(context: Context): String {
        val deps = RuntimeGraph.from(context)
        return RuntimeSettingsCache.getString(
            key = PrefConst.KEY_IPC_TOKEN,
            defaultValue = "",
        ) { key, defaultValue ->
            deps.preferenceDataSource.getString(key, defaultValue)
        }
    }

    private const val WAKEUP_MIN_INTERVAL_MILLIS = 5_000L
}
