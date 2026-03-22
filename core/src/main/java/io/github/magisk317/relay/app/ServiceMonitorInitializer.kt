package io.github.magisk317.relay.app

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.magisk317.relay.common.utils.ActivationDiagnosticsStore
import io.github.magisk317.relay.common.utils.AppPreferencesDataStore
import io.github.magisk317.relay.common.utils.PrefsReader as RelayPrefsReader
import io.github.magisk317.relay.common.utils.RuntimeActivationState
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.domain.recovery.RootDbCatchupScheduler
import io.github.magisk317.relay.feature.call.CallStateMonitor
import io.github.magisk317.relay.platform.reminder.LowBatteryReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ServiceMonitorInitializer : AppInitializer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init(application: Application) {
        initXposedServiceActivationMonitor(application)
        RootDbCatchupScheduler.startPeriodic(application, reason = "app_create")
        LowBatteryReminderScheduler.syncFromPrefs(application, reason = "app_create")
        CallStateMonitor.init(application)
    }

    private fun initXposedServiceActivationMonitor(application: Application) {
        runCatching<Unit> {
            XposedServiceHelper.registerListener(
                object : XposedServiceHelper.OnServiceListener {
                    override fun onServiceBind(service: XposedService) {
                        AppPreferencesDataStore.setRemotePrefsProvider {
                            service.getRemotePreferences("xposed_prefs")
                        }
                        val pending = AppPreferencesDataStore.hasPendingRemoteSync()
                        scope.launch {
                            if (pending) {
                                XLog.w("RemotePrefs sync pending detected; attempting sync on service bind")
                            }
                            AppPreferencesDataStore.syncToRemotePrefs(application)
                        }
                        val verboseLogEnabled = RelayPrefsReader.isVerboseLogMode(application)
                        RuntimeActivationState.setRuntimeActivated(true)
                        ActivationDiagnosticsStore.recordServiceBind(
                            context = application,
                            frameworkName = service.frameworkName,
                            frameworkVersion = service.frameworkVersion,
                            verboseLogging = verboseLogEnabled,
                        )
                        XLog.i(
                            "Xposed service connected: framework=%s version=%s",
                            service.frameworkName,
                            service.frameworkVersion,
                        )
                    }

                    override fun onServiceDied(service: XposedService) {
                        AppPreferencesDataStore.setRemotePrefsProvider(null)
                        RuntimeActivationState.setRuntimeActivated(false)
                        ActivationDiagnosticsStore.recordServiceDied(
                            context = application,
                            verboseLogging = RelayPrefsReader.isVerboseLogMode(application),
                        )
                        XLog.w("Xposed service disconnected")
                    }
                },
            )
        }.onFailure {
            XLog.w("Failed to register Xposed service listener: %s", it.message ?: "unknown")
        }
    }
}
