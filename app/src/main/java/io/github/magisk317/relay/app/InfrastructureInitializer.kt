package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.android.platform.clipboard.AndroidClipboardPlatformBridge
import io.github.magisk317.relay.android.platform.notification.AndroidNotificationPlatformBridge
import io.github.magisk317.relay.android.platform.xpbridge.AndroidXpPrefsBridge
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xpbridge.XpClipboard
import io.github.magisk317.relay.xpbridge.XpNotificationBridge
import io.github.magisk317.relay.xpbridge.XpPrefs

class InfrastructureInitializer : AppInitializer {
    override fun init(application: Application) {
        XpClipboard.installPlatformBridge(AndroidClipboardPlatformBridge)
        XpNotificationBridge.installPlatformBridge(AndroidNotificationPlatformBridge)
        XpPrefs.installPlatformBridge(AndroidXpPrefsBridge)
        AppInfrastructureCoordinator.initialize(
            application = application,
            shouldSuppressSystemHooks = ModuleConflictArbiter::shouldSuppressByRelay,
        )
    }
}
