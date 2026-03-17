package io.github.magisk317.relay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.feature.call.CallStateMonitor

class CallAlertMonitorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrefConst.ACTION_CALL_ALERT_MONITOR_REFRESH) return
        CallStateMonitor.init(context)
        CallStateMonitor.refresh("permission_result")
    }
}
