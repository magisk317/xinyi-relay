package io.github.magisk317.relay.receiver

import android.content.Context
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.feature.call.CallStateMonitor
import io.github.magisk317.xposed.logging.MagiskOtel

object CallAlertMonitorRefreshHandler {
    const val action: String = PrefConst.ACTION_CALL_ALERT_MONITOR_REFRESH

    fun handle(context: Context) {
        CallStateMonitor.init(context)
        CallStateMonitor.refresh("permission_result")
        MagiskOtel.event(
            name = "call.alert",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "refresh",
                "reason" to "permission_result",
            ),
            statusOk = true,
        )
    }
}
