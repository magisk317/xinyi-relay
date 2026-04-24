package io.github.magisk317.relay.receiver

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.feature.reminder.BatteryReminderHandler
import io.github.magisk317.relay.platform.reminder.LowBatteryReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object LowBatteryReminderAlarmHandler {
    const val action: String = PrefConst.ACTION_LOW_BATTERY_REMINDER
    private val alarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handleAsync(context: Context, onComplete: (() -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        alarmScope.launch {
            runCatching {
                val batteryIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (batteryIntent == null) {
                    LowBatteryReminderScheduler.scheduleNext(
                        appContext,
                        reason = "battery_missing",
                        immediate = false,
                    )
                    return@runCatching
                }
                BatteryReminderHandler(
                    context = appContext,
                    eventPipeline = RuntimeGraph.from(appContext).eventPipeline,
                ).handle(
                    batteryIntent = batteryIntent,
                    scheduleNext = true,
                    reason = "alarm_cycle",
                )
            }.onFailure {
                XLog.e("LowBatteryReminder alarm handling failed", it)
            }
            onComplete?.invoke()
        }
    }
}
