package io.github.magisk317.relay.feature.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.magisk317.relay.common.utils.XLog

object BatteryReminderForegroundMonitor {
    @Volatile
    private var registered = false
    private var receiver: BroadcastReceiver? = null

    fun start(context: Context) {
        if (registered) return
        val appContext = context.applicationContext ?: context
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
                BatteryReminderHandler.handle(
                    context = appContext,
                    batteryIntent = intent,
                    scheduleNext = false,
                    reason = "foreground",
                )
            }
        }
        appContext.registerReceiver(newReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        receiver = newReceiver
        registered = true
        XLog.i("Battery reminder foreground monitor started")
    }

    fun stop(context: Context) {
        if (!registered) return
        val appContext = context.applicationContext ?: context
        runCatching {
            receiver?.let { appContext.unregisterReceiver(it) }
        }.onFailure {
            XLog.w("Battery reminder foreground monitor stop failed: %s", it.message ?: "unknown")
        }
        receiver = null
        registered = false
        XLog.i("Battery reminder foreground monitor stopped")
    }
}
