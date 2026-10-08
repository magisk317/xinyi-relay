package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.domain.recovery.RootDbCatchupScheduler
import io.github.magisk317.relay.platform.reminder.LowBatteryReminderScheduler
import io.github.magisk317.xposed.logging.MagiskOtel
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
        MagiskOtel.event(
            name = "app.monitor",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "service_init",
                "reason" to "app_create",
            ),
            statusOk = true,
        )
    }
}
