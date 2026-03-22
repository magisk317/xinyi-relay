package io.github.magisk317.relay.receiver

import android.content.Context
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.feature.call.CallStateMonitor

object CallAlertMonitorRefreshHandler {
    const val action: String = PrefConst.ACTION_CALL_ALERT_MONITOR_REFRESH

    fun handle(context: Context) {
        CallStateMonitor.init(context)
        CallStateMonitor.refresh("permission_result")
    }
}
