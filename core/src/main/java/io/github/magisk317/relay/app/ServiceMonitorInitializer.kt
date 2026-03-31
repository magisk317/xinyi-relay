package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.domain.recovery.RootDbCatchupScheduler
import io.github.magisk317.relay.feature.call.CallStateMonitor
import io.github.magisk317.relay.platform.reminder.LowBatteryReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ServiceMonitorInitializer : AppInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(application: Application) {
        RootDbCatchupScheduler.startPeriodic(application, reason = "app_create")
        AppInitExecution.runWhenUserUnlocked(application, scope, "ServiceMonitorInitializer") {
            LowBatteryReminderScheduler.syncFromPrefs(application, reason = "app_create")
        }
        CallStateMonitor.init(application)
    }
}
