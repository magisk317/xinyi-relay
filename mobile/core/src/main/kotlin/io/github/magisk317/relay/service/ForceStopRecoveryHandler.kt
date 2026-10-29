package io.github.magisk317.relay.service

import io.github.magisk317.relay.android.diagnostics.RuntimeDiagnosticsBridge
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.domain.recovery.RootDbCatchupScheduler
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.forward.security.IpcTokenGate
import io.github.magisk317.xposed.runtime.contract.logging.LogRoute
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
    private val admissionGate = ProcessEventAdmissionGate()

    fun handle(context: Context, intent: Intent, tag: String) {
        if (!admissionGate.tryEnter()) return
        scope.launch {
            try {
                if (admit(context, intent, tag)) {
                    handleInternal(context, intent, tag)
                }
            } finally {
                admissionGate.leave()
            }
        }
    }

    private suspend fun admit(context: Context, intent: Intent, tag: String): Boolean {
        val expectedToken = loadExpectedToken(context)
        val receivedToken = intent.getStringExtra(ForceStopRecoveryContract.EXTRA_IPC_TOKEN)
        val tokenAccepted = IpcTokenGate.isAccepted(
            expectedToken = expectedToken,
            receivedToken = receivedToken,
        )
        return when (recoveryAdmission(tokenAccepted, wakeupThrottle)) {
            RecoveryAdmission.TOKEN_REJECTED -> {
                XLog.log(
                    Log.WARN, LogRoute.ROOT_DB.id, true, true,
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
                false
            }
            RecoveryAdmission.THROTTLED -> {
                XLog.log(android.util.Log.WARN, LogRoute.ROOT_DB.id, true, true, "ForceStopRecoveryService wakeup throttled")
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
                false
            }
            RecoveryAdmission.ADMITTED -> true
        }
    }

    private suspend fun handleInternal(context: Context, intent: Intent, tag: String) {
        val reason = intent.getStringExtra(ForceStopRecoveryContract.EXTRA_REASON).orEmpty()
        val eventId = intent.getStringExtra(ForceStopRecoveryContract.EXTRA_EVENT_ID).orEmpty()
        XLog.log(
            Log.WARN, LogRoute.ROOT_DB.id, true, true, "ForceStopRecoveryService started. reason=%s event=%s",
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
