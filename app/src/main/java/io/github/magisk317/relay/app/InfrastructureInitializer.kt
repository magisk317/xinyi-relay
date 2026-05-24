package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.android.platform.notification.AndroidNotificationPlatformBridge
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xpbridge.XpNotificationBridge

class InfrastructureInitializer : AppInitializer {
    override fun init(application: Application) {
        XpNotificationBridge.installPlatformBridge(AndroidNotificationPlatformBridge)
        AppInfrastructureCoordinator.initialize(
            application = application,
            shouldSuppressSystemHooks = ModuleConflictArbiter::shouldSuppressByRelay,
        )
    }
}
