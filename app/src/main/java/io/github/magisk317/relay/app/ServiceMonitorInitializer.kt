package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.common.utils.ModuleUtils
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.feature.call.CallStateMonitor
import io.github.magisk317.relay.feature.reminder.LowBatteryReminderScheduler
import io.github.magisk317.relay.domain.recovery.RootDbCatchupScheduler
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class ServiceMonitorInitializer : AppInitializer {
    override fun init(application: Application) {
        initXposedServiceActivationMonitor()
        RootDbCatchupScheduler.startPeriodic(application, reason = "app_create")
        LowBatteryReminderScheduler.syncFromPrefs(application, reason = "app_create")
        CallStateMonitor.init(application)
    }

    private fun initXposedServiceActivationMonitor() {
        runCatching<Unit> {
            XposedServiceHelper.registerListener(
                object : XposedServiceHelper.OnServiceListener {
                    override fun onServiceBind(service: XposedService) {
                        ModuleUtils.setRuntimeActivated(true)
                        XLog.i(
                            "Xposed service connected: framework=%s version=%s",
                            service.frameworkName,
                            service.frameworkVersion,
                        )
                    }

                    override fun onServiceDied(service: XposedService) {
                        ModuleUtils.setRuntimeActivated(false)
                        XLog.w("Xposed service disconnected")
                    }
                },
            )
        }.onFailure {
            XLog.w("Failed to register Xposed service listener: %s", it.message ?: "unknown")
        }
    }
}
