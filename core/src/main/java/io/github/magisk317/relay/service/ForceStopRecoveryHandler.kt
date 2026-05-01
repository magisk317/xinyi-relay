package io.github.magisk317.relay.service

import android.content.Context
import android.util.Log
import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.domain.recovery.RootDbCatchupScheduler

object ForceStopRecoveryHandler {
    fun handle(context: Context, reason: String, eventId: String, tag: String) {
        XLog.w(
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
}
