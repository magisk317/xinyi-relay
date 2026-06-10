package io.github.magisk317.relay.feature.reminder

import android.content.Context
import io.github.magisk317.relay.platform.reminder.LowBatteryReminderScheduler

object BatteryReminderSchedulerFacade {
    fun scheduleNext(context: Context, reason: String, immediate: Boolean) {
        LowBatteryReminderScheduler.scheduleNext(context, reason, immediate)
    }

    fun syncFromPrefs(context: Context, reason: String) {
        LowBatteryReminderScheduler.syncFromPrefs(context, reason)
    }
}
