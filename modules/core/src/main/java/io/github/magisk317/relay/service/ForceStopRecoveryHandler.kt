package io.github.magisk317.relay.service

import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.domain.recovery.RootDbCatchupScheduler
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.relay.security.IpcTokenGate
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import kotlinx.coroutines.runBlocking

object ForceStopRecoveryHandler {
    fun handle(context: Context, intent: Intent, tag: String) {
        val expectedToken = loadExpectedToken(context)
        val receivedToken = intent.getStringExtra(ForceStopRecoveryContract.EXTRA_IPC_TOKEN)
        val tokenDecision = IpcTokenGate.evaluate(
            expectedToken = expectedToken,
            receivedToken = receivedToken,
        )
        val reason = intent.getStringExtra(ForceStopRecoveryContract.EXTRA_REASON).orEmpty()
        val eventId = intent.getStringExtra(ForceStopRecoveryContract.EXTRA_EVENT_ID).orEmpty()
        if (!tokenDecision.accepted) {
            XLog.w(
                LogRoute.ROOT_DB,
                "ForceStopRecoveryService rejected token. reason=%s event=%s expectedEmpty=%s receivedEmpty=%s",
                reason.ifBlank { "<none>" },
                eventId.ifBlank { "<none>" },
                expectedToken.isBlank(),
                receivedToken.isNullOrBlank(),
            )
            return
        }
        if (tokenDecision.compatBypassUsed) {
            XLog.w(
                LogRoute.ROOT_DB,
                "ForceStopRecoveryService accepted legacy empty-token compat wakeup. reason=%s event=%s",
                reason.ifBlank { "<none>" },
                eventId.ifBlank { "<none>" },
            )
        }
        XLog.w(
            LogRoute.ROOT_DB,
            "ForceStopRecoveryService started. reason=%s event=%s",
            reason.ifBlank { "<none>" },
            eventId.ifBlank { "<none>" },
        )
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
    }

    private fun loadExpectedToken(context: Context): String {
        val runtimeGraph = RuntimeGraph.from(context)
        return runBlocking {
            RuntimeSettingsCache.getString(
                key = PrefConst.KEY_IPC_TOKEN,
                defaultValue = "",
            ) { key, defaultValue ->
                runtimeGraph.preferenceDataSource.getString(key, defaultValue)
            }
        }
    }
}
