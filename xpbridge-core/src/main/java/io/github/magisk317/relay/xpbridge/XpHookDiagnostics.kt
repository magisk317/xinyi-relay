package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.relay.diagnostics.RuntimeLogStore
import io.github.magisk317.smscode.xposed.runtime.CoreLogSink
import io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder

object XpHookDiagnostics {
    fun installXposedRuntimeLogSink() {
        CoreLogSinkHolder.install(
            object : CoreLogSink {
                override fun append(priority: Int, tag: String, message: String) {
                    RuntimeLogStore.append(
                        priority = priority,
                        tag = tag,
                        message = message,
                        force = true,
                        route = RuntimeLogStore.routeFromCallerClassName(resolveCallerClassName()),
                    )
                }
            },
        )
    }

    fun bindRuntimeLogContext(
        context: Context,
        verboseLogging: Boolean,
    ) {
        RuntimeLogStore.initialize(context, enableDetailedLogs = verboseLogging)
    }

    fun recordSmsHookHeartbeat(
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
