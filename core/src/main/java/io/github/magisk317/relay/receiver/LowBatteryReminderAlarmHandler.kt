package io.github.magisk317.relay.receiver

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.feature.reminder.BatteryReminderHandler
import io.github.magisk317.relay.platform.reminder.LowBatteryReminderScheduler

object LowBatteryReminderAlarmHandler {
    const val action: String = PrefConst.ACTION_LOW_BATTERY_REMINDER

    fun handle(context: Context) {
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
