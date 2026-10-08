package io.github.magisk317.relay.app

import android.content.Context
import io.github.magisk317.relay.android.platform.clipboard.AndroidClipboardPlatformBridge
import io.github.magisk317.relay.android.platform.notification.AndroidNotificationPlatformBridge
import io.github.magisk317.relay.android.platform.xpbridge.AndroidXpPrefsBridge
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xpbridge.XpClipboard
import io.github.magisk317.relay.xpbridge.XpNotificationBridge
import io.github.magisk317.relay.xpbridge.XpPrefs

object FlavorXposedRuntimeInitializer {
    fun installPlatformBridges() {
        XpClipboard.installPlatformBridge(AndroidClipboardPlatformBridge)
        XpNotificationBridge.installPlatformBridge(AndroidNotificationPlatformBridge)
        XpPrefs.installPlatformBridge(AndroidXpPrefsBridge)
    }

    fun shouldSuppressSystemHooks(context: Context?, source: String): Boolean {
        return ModuleConflictArbiter.shouldSuppressByRelay(context, source)
    }
}
