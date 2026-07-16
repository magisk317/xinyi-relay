package io.github.magisk317.relay.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.feature.call.CallStateMonitor
import io.github.magisk317.relay.feature.mode.BatteryOptimizationHelper
import io.github.magisk317.relay.feature.mode.WorkMode
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import io.github.magisk317.relay.service.AppNotificationListenerService
import io.github.magisk317.relay.service.StandardModeService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        XLog.i("BootCompletedReceiver: device boot completed")

        WorkModeResolver.resolve(context)
        val mode = WorkModeResolver.mode.value

        if (mode != WorkMode.Standard) {
            XLog.i("BootCompletedReceiver: mode=%s, skipping standard init", mode)
            return
        }

        // Each standard capability owns its permission check; do not disable the whole mode.
        CallStateMonitor.init(context)

        // Verify NLS binding
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, AppNotificationListenerService::class.java)
            )
        }.onFailure {
            XLog.e("BootCompletedReceiver: NLS requestRebind failed", it)
        }

        // Check battery optimization - log warning if not exempted
        if (!BatteryOptimizationHelper.isExempted(context)) {
            XLog.w("BootCompletedReceiver: app is NOT exempted from battery optimization, " +
                "standard mode may be killed by system. User should grant battery optimization exemption.")
        }

        // Start foreground service to keep standard mode alive
        StandardModeService.reconcile(context, mode, "boot_completed")
    }
}
