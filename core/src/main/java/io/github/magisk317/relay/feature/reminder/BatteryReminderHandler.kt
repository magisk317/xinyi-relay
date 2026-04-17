package io.github.magisk317.relay.feature.reminder

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.BatteryManager
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.domain.pipeline.EventPipeline
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.relay.platform.reminder.LowBatteryReminderScheduler
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * 电量提醒处理器。
 *
 * 内部状态位（KEY_INTERNAL_LOW_BATTERY_BELOW / KEY_INTERNAL_FULL_BATTERY_ABOVE）使用同步
 * SharedPreferences 读写，避免在非协程上下文中调用 DataStore 引发 runBlocking。
 */
class BatteryReminderHandler(
    private val context: Context,
    private val eventPipeline: EventPipeline,
) {
    fun handle(
        batteryIntent: Intent,
        scheduleNext: Boolean,
        reason: String,
    ) {
        val settings = runBlocking {
            RuntimeSettingsCache.getSpecialAlertSettings(
                RuntimeGraph.from(context).settingsRepository,
            )
        }
        val lowEnabled = settings.lowBatteryReminderEnabled
        val fullEnabled = settings.fullBatteryReminderEnabled
        if (!lowEnabled && !fullEnabled) {
            if (scheduleNext) {
                LowBatteryReminderScheduler.cancel(context, reason = "disabled:$reason")
            }
            return
        }
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0 || scale <= 0) {
            if (scheduleNext) {
                LowBatteryReminderScheduler.scheduleNext(
                    context,
                    reason = "battery_invalid:$reason",
                    immediate = false,
                )
            }
            return
        }
        val percent = (level * 100f / scale).toInt()

        // 同步读写内部状态位，避免在普通函数中嵌套 runBlocking 操作 DataStore
        val prefs = context.getSharedPreferences("xposed_prefs", Context.MODE_PRIVATE)

        val threshold = if (lowEnabled) settings.lowBatteryThreshold else 0
        val wasBelow = if (lowEnabled) prefs.safeGetBoolean(PrefConst.KEY_INTERNAL_LOW_BATTERY_BELOW, false) else false
        val wasFull = if (fullEnabled) prefs.safeGetBoolean(PrefConst.KEY_INTERNAL_FULL_BATTERY_ABOVE, false) else false

        if (lowEnabled) {
            if (percent <= threshold) {
                if (!wasBelow) {
                    sendLowReminder(percent, threshold)
                    prefs.edit().putBoolean(PrefConst.KEY_INTERNAL_LOW_BATTERY_BELOW, true).apply()
                }
            } else if (wasBelow) {
                prefs.edit().putBoolean(PrefConst.KEY_INTERNAL_LOW_BATTERY_BELOW, false).apply()
            }
        }

        if (fullEnabled) {
            val isFull = percent >= 100 || status == BatteryManager.BATTERY_STATUS_FULL
            if (isFull) {
                if (!wasFull) {
                    sendFullReminder(percent)
                    prefs.edit().putBoolean(PrefConst.KEY_INTERNAL_FULL_BATTERY_ABOVE, true).apply()
                }
            } else if (wasFull) {
                prefs.edit().putBoolean(PrefConst.KEY_INTERNAL_FULL_BATTERY_ABOVE, false).apply()
            }
        }

        if (scheduleNext) {
            LowBatteryReminderScheduler.scheduleNext(context, reason = "cycle:$reason", immediate = false)
        }
    }

    private fun sendLowReminder(percent: Int, threshold: Int) {
        val senderId = runBlocking {
            RuntimeSettingsCache.getSpecialAlertSettings(
                RuntimeGraph.from(context).settingsRepository,
            ).lowBatteryChannelId.trim().toLongOrNull()
        }
        if (senderId == null) {
            XLog.w("LowBattery reminder skipped: sender not set")
            return
        }
        val title = context.getString(R.string.low_battery_notification_title)
        val content = context.getString(R.string.low_battery_notification_content, percent, threshold)
        runBlocking {
            eventPipeline.process(
                event = RelayEvent.batteryReminder(
                    title = title,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    packageName = context.packageName,
                    senderId = senderId,
                    sourceType = "battery_low",
                ),
                traceId = "low_battery_${System.currentTimeMillis()}",
            )
        }
        XLog.i("LowBattery reminder routed through EventPipeline: pct=%d threshold=%d", percent, threshold)
    }

    private fun sendFullReminder(percent: Int) {
        val senderId = runBlocking {
            RuntimeSettingsCache.getSpecialAlertSettings(
                RuntimeGraph.from(context).settingsRepository,
            ).fullBatteryChannelId.trim().toLongOrNull()
        }
        if (senderId == null) {
            XLog.w("FullBattery reminder skipped: sender not set")
            return
        }
        val title = context.getString(R.string.full_battery_notification_title)
        val content = context.getString(R.string.full_battery_notification_content, percent)
        runBlocking {
            eventPipeline.process(
                event = RelayEvent.batteryReminder(
                    title = title,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    packageName = context.packageName,
                    senderId = senderId,
                    sourceType = "battery_full",
                ),
                traceId = "full_battery_${System.currentTimeMillis()}",
            )
        }
        XLog.i("FullBattery reminder routed through EventPipeline: pct=%d", percent)
    }

    companion object {
        private fun SharedPreferences.safeGetBoolean(key: String, defaultValue: Boolean): Boolean {
            val rawValue = all[key] ?: return defaultValue
            return when (rawValue) {
                is Boolean -> rawValue
                is Number -> rawValue.toInt() != 0
                is String -> parseBoolean(rawValue) ?: defaultValue
                else -> defaultValue
            }
        }

        private fun parseBoolean(rawValue: String): Boolean? {
            return when (rawValue.trim().lowercase(Locale.ROOT)) {
                "1", "true", "yes", "y", "on" -> true
                "0", "false", "no", "n", "off" -> false
                else -> null
            }
        }

        fun handle(
            context: Context,
            batteryIntent: Intent,
            scheduleNext: Boolean,
            reason: String,
        ) {
            BatteryReminderHandler(
                context = context,
                eventPipeline = RuntimeGraph.from(context).eventPipeline,
            ).handle(
                batteryIntent = batteryIntent,
                scheduleNext = scheduleNext,
                reason = reason,
            )
        }
    }
}
