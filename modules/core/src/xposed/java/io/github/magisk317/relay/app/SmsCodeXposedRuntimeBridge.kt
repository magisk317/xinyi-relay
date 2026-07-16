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

object SmsCodeXposedRuntimeBridge {
    fun install(
        shouldSuppressSystemHooks: (Context?, String) -> Boolean,
    ) {
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
