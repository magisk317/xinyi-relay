package io.github.magisk317.relay.ui.app

import kotlinx.coroutines.CoroutineScope

internal object FlavorXposedServiceBridge {
    fun initialize(application: SmsCodeApplication, applicationScope: CoroutineScope) {
        // Legacy builds rely on classic Xposed entry points and do not bind libxposed services.
    }
}
