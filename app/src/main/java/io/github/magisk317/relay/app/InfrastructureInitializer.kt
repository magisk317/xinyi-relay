package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.analytics.AnalyticsTracker
import io.github.magisk317.relay.common.utils.RuntimeLogStore
import timber.log.Timber

class InfrastructureInitializer : AppInitializer {
    override fun init(application: Application) {
        AnalyticsTracker.init(application)
        RuntimeLogStore.initialize(application, enableDetailedLogs = false)
        if (io.github.magisk317.relay.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
