package io.github.magisk317.relay.receiver

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.app.InfrastructureInitializer
import io.github.magisk317.relay.service.AppNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        XLog.i("BootCompletedReceiver: device boot completed")

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val application = appContext as? Application
                if (application != null) {
                    InfrastructureInitializer.settleAndReconcile(application, "boot_completed_settled")
                }

                runCatching {
                    NotificationListenerService.requestRebind(
                        ComponentName(appContext, AppNotificationListenerService::class.java),
                    )
                }.onFailure {
                    XLog.e("BootCompletedReceiver: NLS requestRebind failed", it)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
