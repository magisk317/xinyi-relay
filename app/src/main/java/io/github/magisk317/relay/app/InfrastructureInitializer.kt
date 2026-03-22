package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter

class InfrastructureInitializer : AppInitializer {
    override fun init(application: Application) {
        AppInfrastructureCoordinator.initialize(
            application = application,
            shouldSuppressSystemHooks = ModuleConflictArbiter::shouldSuppressByRelay,
        )
    }
}
