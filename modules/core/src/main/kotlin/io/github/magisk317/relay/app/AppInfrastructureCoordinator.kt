package io.github.magisk317.relay.app

import io.github.magisk317.relay.android.diagnostics.RuntimeDiagnosticsBridge
import android.app.Application
import android.content.Context
import io.github.magisk317.relay.analytics.AnalyticsTracker
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.platform.sender.SenderLogBridge
import io.github.magisk317.relay.android.platform.sender.SenderRuntimeBridge
import io.github.magisk317.relay.bootstrap.RuntimeDependencies
import io.github.magisk317.relay.di.RuntimeDependenciesImpl
import io.github.magisk317.smscode.runtime.verification.VerificationLogSink
import io.github.magisk317.smscode.runtime.verification.VerificationLogSinkHolder
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
        SensitiveLogPolicy.setEnabled(false)
        SenderRuntimeBridge.install(application)
        SenderLogBridge.install()
        XLog.configure()
        installVerificationLogSink()
        SmsCodeXposedRuntimeBridge.install(shouldSuppressSystemHooks)
        if (io.github.magisk317.relay.runtime.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun installVerificationLogSink() {
        VerificationLogSinkHolder.install(object : VerificationLogSink {
            override fun append(
                priority: Int,
                tag: String,
                message: String,
                force: Boolean,
                route: String?,
                sensitive: Boolean,
            ) {
                val safeMessage = if (sensitive) SensitiveLogPolicy.sanitizeLogMessage(message) else message
                RuntimeDiagnosticsBridge.ensureInstalled()
                RuntimeLogStore.append(priority, tag, safeMessage, force, route)
            }
        })
    }
}
