package io.github.magisk317.relay.app

import android.app.Application
import io.github.magisk317.relay.analytics.AnalyticsTracker
import io.github.magisk317.relay.common.utils.RuntimeLogStore
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.core.runtime.CoreHookPolicy
import io.github.magisk317.smscode.core.runtime.CoreHookPolicyHolder
import io.github.magisk317.smscode.core.runtime.CoreLogSink
import io.github.magisk317.smscode.core.runtime.CoreLogSinkHolder
import io.github.magisk317.smscode.core.runtime.CoreRuntime
import io.github.magisk317.smscode.core.runtime.CoreRuntimeAccess
import timber.log.Timber

class InfrastructureInitializer : AppInitializer {
    override fun init(application: Application) {
        AnalyticsTracker.init(application)
        RuntimeLogStore.initialize(application, enableDetailedLogs = false)
        installCoreRuntime()
        if (io.github.magisk317.relay.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun installCoreRuntime() {
        CoreRuntime.install(object : CoreRuntimeAccess {
            override val logTag: String = io.github.magisk317.relay.BuildConfig.LOG_TAG
            override val logLevel: Int = io.github.magisk317.relay.BuildConfig.LOG_LEVEL
            override val logToXposed: Boolean = io.github.magisk317.relay.BuildConfig.LOG_TO_XPOSED
            override val debug: Boolean = io.github.magisk317.relay.BuildConfig.DEBUG
            override val applicationId: String = io.github.magisk317.relay.BuildConfig.APPLICATION_ID
            override val actionNamespace: String = "io.github.magisk317.relay"
        })
        CoreLogSinkHolder.install(object : CoreLogSink {
            override fun append(priority: Int, tag: String, message: String) {
                RuntimeLogStore.append(priority, tag, message)
            }
        })
        CoreHookPolicyHolder.install(object : CoreHookPolicy {
            override fun shouldSuppressSystemHooks(context: android.content.Context?, source: String): Boolean {
                return ModuleConflictArbiter.shouldSuppressByRelay(context, source)
            }
        })
    }
}
