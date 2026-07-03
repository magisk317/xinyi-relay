package io.github.magisk317.relay.ui.app

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.magisk317.relay.app.XposedServiceRuntimeCoordinator
import kotlinx.coroutines.CoroutineScope

internal object XposedServiceBridge {
    fun initialize(application: SmsCodeApplication, applicationScope: CoroutineScope) {
        runCatching<Unit> {
            XposedServiceHelper.registerListener(
                object : XposedServiceHelper.OnServiceListener {
                    override fun onServiceBind(service: XposedService) {
                        XposedServiceRuntimeCoordinator.handleServiceBound(
                            application = application,
                            applicationScope = applicationScope,
                            remotePrefsProvider = { service.getRemotePreferences("xposed_prefs") },
                            frameworkName = service.frameworkName,
                            frameworkVersion = service.frameworkVersion,
                        )
                    }

                    override fun onServiceDied(service: XposedService) {
                        XposedServiceRuntimeCoordinator.handleServiceDied(application)
                    }
                },
            )
        }.onFailure(XposedServiceRuntimeCoordinator::logRegistrationFailure)
    }
}
