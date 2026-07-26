package io.github.magisk317.relay.app

import io.github.magisk317.relay.android.diagnostics.RuntimeDiagnosticsBridge
import android.content.Context
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.receiver.AutoInputActions
import io.github.magisk317.smscode.xposed.runtime.CoreHookPolicy
import io.github.magisk317.smscode.xposed.runtime.CoreHookPolicyHolder
import io.github.magisk317.smscode.xposed.runtime.CoreLogSink
import io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.smscode.xposed.runtime.CoreRuntimeAccess
import io.github.magisk317.relay.android.otel.MagiskOtelBootstrap
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.AnonymousInstallationId

object SmsCodeXposedRuntimeBridge {
    fun install(
        shouldSuppressSystemHooks: (Context?, String) -> Boolean,
    ) {
        // Hook process: default ON (release pref default true). Prefer prefs when readable.
        val hookContext = runCatching {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull()
        val otelEnabled = if (hookContext != null) {
            MagiskOtelBootstrap.isEnabled(hookContext)
        } else {
            MagiskOtelBootstrap.isEffectivelyEnabled(userPrefEnabled = true)
        }
        MagiskOtel.configureIfAbsent(
            MagiskOtel.Config(
                enabled = otelEnabled,
                serviceName = "xinyi-relay",
                serviceVersion = io.github.magisk317.relay.core.BuildConfig.VERSION_NAME,
                projectId = "84113188",
                projectName = "xinyi-relay",
                environment = if (io.github.magisk317.relay.runtime.BuildConfig.DEBUG) "debug" else "release",
                serviceInstanceId = hookContext?.let {
                    io.github.magisk317.relay.android.prefs.PrefsReader.installationId(it)
                }.orEmpty(),
            ),
        )
        CoreRuntime.install(object : CoreRuntimeAccess {
            override val logTag: String = io.github.magisk317.relay.runtime.BuildConfig.LOG_TAG
            override val logLevel: Int = io.github.magisk317.relay.runtime.BuildConfig.LOG_LEVEL
            override val logToXposed: Boolean = io.github.magisk317.relay.runtime.BuildConfig.LOG_TO_XPOSED
            override val debug: Boolean = io.github.magisk317.relay.runtime.BuildConfig.DEBUG
            override val applicationId: String = io.github.magisk317.relay.runtime.BuildConfig.APPLICATION_ID
            override val actionNamespace: String = AutoInputActions.ACTION_NAMESPACE
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
                RuntimeDiagnosticsBridge.ensureInstalled()
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
