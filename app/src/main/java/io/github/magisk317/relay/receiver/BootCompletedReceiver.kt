package io.github.magisk317.relay.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.feature.call.CallStateMonitor
import io.github.magisk317.relay.feature.mode.StandardModePermissions
import io.github.magisk317.relay.feature.mode.WorkMode
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import io.github.magisk317.relay.service.AppNotificationListenerService

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

        // Initialize call state monitoring
        if (StandardModePermissions.allGranted(context)) {
            CallStateMonitor.init(context)
        } else {
            XLog.w("BootCompletedReceiver: permissions missing, skip CallStateMonitor")
        }

        // Verify NLS binding
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, AppNotificationListenerService::class.java)
            )
        }.onFailure {
            XLog.e("BootCompletedReceiver: NLS requestRebind failed", it)
        }
    }
}
