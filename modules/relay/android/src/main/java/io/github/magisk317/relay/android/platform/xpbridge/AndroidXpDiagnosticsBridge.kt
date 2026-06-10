package io.github.magisk317.relay.android.platform.xpbridge

import android.content.Context
import android.util.Log
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.android.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore
import io.github.magisk317.relay.contract.xpbridge.XpDiagnosticsRuntimeBridge

object AndroidXpDiagnosticsBridge : XpDiagnosticsRuntimeBridge {
    override fun appendXposedLog(
        priority: Int,
        tag: String,
        message: String,
        force: Boolean,
        route: String?,
        sensitive: Boolean,
        callerClassName: String?,
    ) {
        val safeMessage = if (sensitive) SensitiveLogPolicy.sanitizeLogMessage(message) else message
        RuntimeLogStore.append(
            priority = priority,
            tag = tag,
            message = safeMessage,
            force = force || priority >= Log.WARN,
            route = route ?: RuntimeLogStore.routeFromCallerClassName(callerClassName),
        )
    }

    override fun bindRuntimeLogContext(
        context: Context,
        verboseLogging: Boolean,
    ) {
        RuntimeLogStore.initialize(context, enableDetailedLogs = verboseLogging)
    }

    override fun recordSmsHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
    ) {
        ActivationDiagnosticsStore.recordHookHeartbeat(
            context = context,
            packageName = packageName,
            processName = processName,
            source = source,
            verboseLogging = verboseLogging,
            route = RuntimeLogStore.ROUTE_SMS_HOOK,
        )
    }
}
