package io.github.magisk317.relay.feature.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import kotlinx.coroutines.runBlocking

object LowBatteryReminderScheduler {

    private const val REQUEST_CODE = 4101
    private const val DEFAULT_INTERVAL_MIN = 15L
    private const val IMMEDIATE_DELAY_MS = 5_000L

    fun syncFromPrefs(context: Context, reason: String) {
        val settings = runBlocking {
            RuntimeSettingsCache.getSpecialAlertSettings(
                RuntimeGraph.from(context).settingsRepository,
            )
        }
        if (settings.lowBatteryReminderEnabled || settings.fullBatteryReminderEnabled) {
            scheduleNext(context, reason = "sync:$reason", immediate = false)
        } else {
            cancel(context, reason = "sync:$reason")
        }
    }

    fun scheduleNext(context: Context, reason: String, immediate: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager? ?: return
        val pendingIntent = buildPendingIntent(context)
        val delayMs = if (immediate) IMMEDIATE_DELAY_MS else DEFAULT_INTERVAL_MIN * 60_000L
        val triggerAt = System.currentTimeMillis() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                XLog.i("LowBattery reminder scheduled inexact (no exact alarm access)")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            XLog.w("LowBattery reminder fallback inexact: %s", e.message ?: "unknown")
        }
        XLog.i(
            "LowBattery reminder scheduled: triggerAt=%d delayMs=%d reason=%s",
            triggerAt,
            delayMs,
            reason,
        )
    }

    fun cancel(context: Context, reason: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager? ?: return
        val pendingIntent = buildPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        XLog.i("LowBattery reminder cancelled reason=%s", reason)
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(PrefConst.ACTION_LOW_BATTERY_REMINDER).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
