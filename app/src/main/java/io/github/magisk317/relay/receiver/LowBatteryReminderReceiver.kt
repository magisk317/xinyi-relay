package io.github.magisk317.relay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.feature.reminder.BatteryReminderHandler
import io.github.magisk317.relay.feature.reminder.LowBatteryReminderScheduler

class LowBatteryReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PrefConst.ACTION_LOW_BATTERY_REMINDER) return
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryIntent == null) {
            LowBatteryReminderScheduler.scheduleNext(context, reason = "battery_missing", immediate = false)
            return
        }
        BatteryReminderHandler.handle(
            context = context,
            batteryIntent = batteryIntent,
            scheduleNext = true,
            reason = "alarm_cycle",
        )
    }
}
