package io.github.magisk317.relay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LowBatteryReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LowBatteryReminderAlarmHandler.action) return
        val pendingResult = goAsync()
        LowBatteryReminderAlarmHandler.handleAsync(context) {
            pendingResult.finish()
        }
    }
}
