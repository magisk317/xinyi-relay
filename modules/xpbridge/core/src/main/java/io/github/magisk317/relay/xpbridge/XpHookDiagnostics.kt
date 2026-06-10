package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.contract.xpbridge.NoopXpDiagnosticsRuntimeBridge
import io.github.magisk317.relay.contract.xpbridge.XpDiagnosticsRuntimeBridge
import io.github.magisk317.smscode.xposed.runtime.CoreLogSink
import io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder

object XpHookDiagnostics {
    @Volatile
    private var runtimeBridge: XpDiagnosticsRuntimeBridge = NoopXpDiagnosticsRuntimeBridge

    fun installRuntimeBridge(bridge: XpDiagnosticsRuntimeBridge?) {
        runtimeBridge = bridge ?: NoopXpDiagnosticsRuntimeBridge
    }

    fun installXposedRuntimeLogSink() {
        CoreLogSinkHolder.install(
            object : CoreLogSink {
                override fun append(
                    priority: Int,
                    tag: String,
                    message: String,
                    force: Boolean,
                    route: String?,
                    sensitive: Boolean,
                ) {
                    runtimeBridge.appendXposedLog(
                        priority = priority,
                        tag = tag,
                        message = message,
                        force = force,
                        route = route,
                        sensitive = sensitive,
                        callerClassName = resolveCallerClassName(),
                    )
                }
            },
        )
    }

    fun bindRuntimeLogContext(
        context: Context,
        verboseLogging: Boolean,
    ) {
        runtimeBridge.bindRuntimeLogContext(context, verboseLogging)
    }

    fun recordSmsHookHeartbeat(
        context: Context,
        packageName: String,
        processName: String,
        source: String,
        verboseLogging: Boolean,
    ) {
        runtimeBridge.recordSmsHookHeartbeat(
            context = context,
            packageName = packageName,
            processName = processName,
            source = source,
            verboseLogging = verboseLogging,
        )
    }

    private fun resolveCallerClassName(): String? {
        return Throwable().stackTrace
            .mapNotNull { it.className }
            .firstOrNull { className ->
                className != XpHookDiagnostics::class.java.name &&
                    !className.startsWith("${XpHookDiagnostics::class.java.name}\$") &&
                    className != "io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder" &&
                    !className.startsWith("io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder$") &&
                    className != "io.github.magisk317.smscode.xposed.utils.XLog" &&
                    !className.startsWith("io.github.magisk317.smscode.xposed.utils.XLog$")
            }
    }
}
