package io.github.magisk317.relay.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.magisk317.relay.android.platform.clipboard.AndroidClipboardPlatformBridge
import io.github.magisk317.relay.android.platform.notification.AndroidNotificationPlatformBridge
import io.github.magisk317.relay.android.platform.xpbridge.AndroidXpPrefsBridge
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xpbridge.XpClipboard
import io.github.magisk317.relay.xpbridge.XpNotificationBridge
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.feature.call.CallStateMonitor
import io.github.magisk317.relay.feature.mode.WorkMode
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import io.github.magisk317.relay.service.StandardModeService

class InfrastructureInitializer : AppInitializer {
    override fun init(application: Application) {
        XpClipboard.installPlatformBridge(AndroidClipboardPlatformBridge)
        XpNotificationBridge.installPlatformBridge(AndroidNotificationPlatformBridge)
        XpPrefs.installPlatformBridge(AndroidXpPrefsBridge)
        WorkModeResolver.resolve(application)
        CallStateMonitor.init(application)
        AppInfrastructureCoordinator.initialize(
            application = application,
            shouldSuppressSystemHooks = ModuleConflictArbiter::shouldSuppressByRelay,
        )

        // Start foreground service if in Standard mode
        if (WorkModeResolver.mode.value == WorkMode.Standard) {
            StandardModeService.start(application)
        }

        // Register ProcessLifecycleOwner observer to re-evaluate work mode on app resume
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                WorkModeResolver.resolve(application)
                // Re-check mode on resume and start/stop service accordingly
                when (WorkModeResolver.mode.value) {
                    WorkMode.Standard -> StandardModeService.start(application)
                    else -> StandardModeService.stop(application)
                }
            }
        })
    }
}
