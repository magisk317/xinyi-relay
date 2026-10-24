package io.github.magisk317.relay.app

import io.github.magisk317.relay.android.diagnostics.RuntimeDiagnosticsBridge
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import android.app.Application
import android.content.Context
import io.github.magisk317.relay.analytics.AnalyticsTracker
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.relay.android.platform.sender.SenderLogBridge
import io.github.magisk317.relay.android.platform.sender.SenderRuntimeBridge
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.di.RuntimeDependenciesImpl
import org.koin.core.context.GlobalContext
import timber.log.Timber

object AppInfrastructureCoordinator {
    fun initialize(
        application: Application,
        shouldSuppressSystemHooks: (Context?, String) -> Boolean,
    ) {
        // Bridge Koin singletons into :runtime via the RuntimeDependencies interface,
        // so :runtime callers can resolve dependencies without importing Koin directly.
        RuntimeDependencies.register(RuntimeDependenciesImpl(GlobalContext.get()))
        AnalyticsTracker.init(application)
        RuntimeDiagnosticsBridge.ensureInstalled()
        RuntimeLogStore.initialize(application, enableDetailedLogs = false)
        SenderRuntimeBridge.install(application)
        SenderLogBridge.install()
        io.github.magisk317.xposed.logging.XLog.configure(
            tag = io.github.magisk317.relay.android.BuildConfig.LOG_TAG,
            logLevel = io.github.magisk317.relay.android.BuildConfig.LOG_LEVEL,
            logToXposed = io.github.magisk317.relay.android.BuildConfig.LOG_TO_XPOSED,
        )
        installVerificationLogSink()
        SmsCodeXposedRuntimeBridge.install(shouldSuppressSystemHooks)
        if (io.github.magisk317.relay.runtime.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun installVerificationLogSink() {
        io.github.magisk317.xposed.logging.XLog.install(
            io.github.magisk317.xposed.logging.LogSink { event ->
                val safeMessage = if (event.sensitive) {
                    SensitiveLogPolicy.sanitizeLogMessage(event.message)
                } else {
                    event.message
                }
                RuntimeDiagnosticsBridge.ensureInstalled()
                RuntimeLogStore.append(event.priority, event.tag, safeMessage, event.force, event.route)
            },
        )
    }
}
