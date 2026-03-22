package io.github.magisk317.relay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallAlertMonitorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CallAlertMonitorRefreshHandler.action) return
        CallAlertMonitorRefreshHandler.handle(context)
    }
}
