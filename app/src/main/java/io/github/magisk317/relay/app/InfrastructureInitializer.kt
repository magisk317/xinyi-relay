package io.github.magisk317.relay.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.magisk317.relay.feature.call.CallStateMonitor
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import io.github.magisk317.relay.service.StandardModeService

class InfrastructureInitializer : AppInitializer {
    override fun init(application: Application) {
        FlavorXposedRuntimeInitializer.installPlatformBridges()
        WorkModeResolver.resolve(application)
        CallStateMonitor.init(application)
        AppInfrastructureCoordinator.initialize(
            application = application,
            shouldSuppressSystemHooks = FlavorXposedRuntimeInitializer::shouldSuppressSystemHooks,
        )

        // Start foreground service if in Standard mode
        StandardModeService.reconcile(application, WorkModeResolver.mode.value, "app_init")

        // Register ProcessLifecycleOwner observer to re-evaluate work mode on app resume
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                WorkModeResolver.resolve(application)
                // Re-check mode on resume and start/stop service accordingly
                StandardModeService.reconcile(application, WorkModeResolver.mode.value, "process_resume")
            }
        })
    }
}
