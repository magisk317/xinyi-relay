package io.github.magisk317.relay.receiver

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.app.InfrastructureInitializer
import io.github.magisk317.relay.feature.call.CallStateMonitor
import io.github.magisk317.relay.feature.mode.BatteryOptimizationHelper
import io.github.magisk317.relay.feature.mode.WorkMode
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import io.github.magisk317.relay.service.AppNotificationListenerService
import io.github.magisk317.relay.service.StandardModeService
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
                    // Same design as app init: settle environment before Standard-only paths.
                    InfrastructureInitializer.settleAndReconcile(application, "boot_completed_settled")
                } else {
                    WorkModeResolver.resolve(appContext)
                    StandardModeService.reconcile(
                        appContext,
                        WorkModeResolver.mode.value,
                        "boot_completed_fallback",
                    )
                }

                val mode = WorkModeResolver.mode.value
                if (mode != WorkMode.Standard) {
                    XLog.i("BootCompletedReceiver: mode=%s, skipping standard-only init", mode)
                    return@launch
                }

                CallStateMonitor.init(appContext)
                runCatching {
                    NotificationListenerService.requestRebind(
                        ComponentName(appContext, AppNotificationListenerService::class.java),
                    )
                }.onFailure {
                    XLog.e("BootCompletedReceiver: NLS requestRebind failed", it)
                }

                if (!BatteryOptimizationHelper.isExempted(appContext)) {
                    XLog.w(
                        "BootCompletedReceiver: app is NOT exempted from battery optimization, " +
                            "standard mode may be killed by system. User should grant battery optimization exemption.",
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
