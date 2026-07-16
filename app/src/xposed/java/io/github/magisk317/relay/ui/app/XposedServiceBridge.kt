package io.github.magisk317.relay.ui.app

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.magisk317.relay.app.XposedServiceRuntimeCoordinator
import io.github.magisk317.relay.service.StandardModeService
import kotlinx.coroutines.CoroutineScope

internal object XposedServiceBridge {
    fun initialize(application: SmsCodeApplication, applicationScope: CoroutineScope) {
        runCatching<Unit> {
            XposedServiceHelper.registerListener(
                object : XposedServiceHelper.OnServiceListener {
                    override fun onServiceBind(service: XposedService) {
                        val mode = XposedServiceRuntimeCoordinator.handleServiceBound(
                            application = application,
                            applicationScope = applicationScope,
                            remotePrefsProvider = { service.getRemotePreferences("xposed_prefs") },
                            frameworkName = service.frameworkName,
                            frameworkVersion = service.frameworkVersion,
                        )
                        StandardModeService.reconcile(application, mode, "xposed_service_bound")
                        PhoneProcessRestartCoordinator.requestAfterInstallOrUpdate(application, applicationScope)
                    }

                    override fun onServiceDied(service: XposedService) {
                        val mode = XposedServiceRuntimeCoordinator.handleServiceDied(application)
                        StandardModeService.reconcile(application, mode, "xposed_service_died")
                    }
                },
            )
        }.onFailure(XposedServiceRuntimeCoordinator::logRegistrationFailure)
    }
}
