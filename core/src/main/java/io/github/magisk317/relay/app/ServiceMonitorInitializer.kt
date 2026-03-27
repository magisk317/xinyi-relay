package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.domain.recovery.RootDbCatchupScheduler
import io.github.magisk317.relay.feature.call.CallStateMonitor
import io.github.magisk317.relay.platform.reminder.LowBatteryReminderScheduler

class ServiceMonitorInitializer : AppInitializer {
    override fun init(application: Application) {
        RootDbCatchupScheduler.startPeriodic(application, reason = "app_create")
        LowBatteryReminderScheduler.syncFromPrefs(application, reason = "app_create")
        CallStateMonitor.init(application)
    }
}
