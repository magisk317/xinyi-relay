package io.github.magisk317.relay.app

import android.app.Application
import android.content.Context
import io.github.magisk317.relay.analytics.AnalyticsTracker
import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.android.platform.sender.SenderLogBridge
import io.github.magisk317.relay.android.platform.sender.SenderRuntimeBridge
import io.github.magisk317.smscode.xposed.runtime.CoreHookPolicy
import io.github.magisk317.smscode.xposed.runtime.CoreHookPolicyHolder
import io.github.magisk317.smscode.xposed.runtime.CoreLogSink
import io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.smscode.xposed.runtime.CoreRuntimeAccess
import timber.log.Timber

object AppInfrastructureCoordinator {
    fun initialize(
        application: Application,
        shouldSuppressSystemHooks: (Context?, String) -> Boolean,
    ) {
        AnalyticsTracker.init(application)
        RuntimeLogStore.initialize(application, enableDetailedLogs = false)
        SensitiveLogPolicy.setEnabled(false)
        SenderRuntimeBridge.install()
        SenderLogBridge.install()
        installCoreRuntime(shouldSuppressSystemHooks)
        if (io.github.magisk317.relay.runtime.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun installCoreRuntime(
        shouldSuppressSystemHooks: (Context?, String) -> Boolean,
    ) {
        CoreRuntime.install(object : CoreRuntimeAccess {
            override val logTag: String = io.github.magisk317.relay.runtime.BuildConfig.LOG_TAG
            override val logLevel: Int = io.github.magisk317.relay.runtime.BuildConfig.LOG_LEVEL
            override val logToXposed: Boolean = io.github.magisk317.relay.runtime.BuildConfig.LOG_TO_XPOSED
            override val debug: Boolean = io.github.magisk317.relay.runtime.BuildConfig.DEBUG
            override val applicationId: String = io.github.magisk317.relay.runtime.BuildConfig.APPLICATION_ID
            override val actionNamespace: String = "io.github.magisk317.relay"
        })
        CoreLogSinkHolder.install(object : CoreLogSink {
            override fun append(
                priority: Int,
                tag: String,
                message: String,
                force: Boolean,
                route: String?,
                sensitive: Boolean,
            ) {
                val safeMessage = if (sensitive) SensitiveLogPolicy.sanitizeLogMessage(message) else message
                RuntimeLogStore.append(priority, tag, safeMessage, force, route)
            }
        })
        CoreHookPolicyHolder.install(object : CoreHookPolicy {
            override fun shouldSuppressSystemHooks(context: Context?, source: String): Boolean {
                return shouldSuppressSystemHooks(context, source)
            }
        })
    }
}
