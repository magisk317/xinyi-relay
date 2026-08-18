package io.github.magisk317.relay.android.platform.xpbridge

import io.github.magisk317.relay.android.diagnostics.RuntimeDiagnosticsBridge
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.smscode.runtime.common.ipc.RuntimeStateProviderContract
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
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
        RuntimeDiagnosticsBridge.ensureInstalled()
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
        RuntimeDiagnosticsBridge.ensureInstalled()
        RuntimeLogStore.initialize(context, enableDetailedLogs = verboseLogging)
    }

    override fun recordSmsHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
    ) {
        val extras = Bundle().apply {
            putString(RuntimeStateProviderContract.EXTRA_PACKAGE_NAME, packageName)
            putString(RuntimeStateProviderContract.EXTRA_PROCESS_NAME, processName)
            putString(RuntimeStateProviderContract.EXTRA_SOURCE, source)
            putBoolean(RuntimeStateProviderContract.EXTRA_VERBOSE_LOGGING, verboseLogging)
            putString(RuntimeStateProviderContract.EXTRA_ROUTE, RuntimeLogStore.ROUTE_SMS_HOOK)
        }
        val acknowledged = runCatching {
            context.contentResolver.call(
                Uri.parse("content://${context.packageName}.db.provider"),
                RuntimeStateProviderContract.METHOD_RECORD_HOOK_HEARTBEAT,
                null,
                extras,
            )?.getBoolean(RuntimeStateProviderContract.RESULT_OK, false) == true
        }.onFailure { error ->
            XLog.w(
                "Hook heartbeat provider call failed: source=%s err=%s",
                source,
                error.message ?: error.javaClass.simpleName,
            )
        }.getOrDefault(false)
        if (!acknowledged) {
            XLog.w("Hook heartbeat provider call was not acknowledged: source=%s", source)
        }
    }
}
