package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.contract.xpbridge.NoopXpDiagnosticsRuntimeBridge
import io.github.magisk317.relay.contract.xpbridge.XpDiagnosticsRuntimeBridge
import io.github.magisk317.smscode.xposed.runtime.CoreLogSink
import io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder
import io.github.magisk317.xposed.logging.XposedLogClient

object XpHookDiagnostics {
    @Volatile
    private var runtimeBridge: XpDiagnosticsRuntimeBridge = NoopXpDiagnosticsRuntimeBridge

    fun installRuntimeBridge(bridge: XpDiagnosticsRuntimeBridge?) {
        runtimeBridge = bridge ?: NoopXpDiagnosticsRuntimeBridge
    }

    fun configureLogClient(authority: String, source: String = "xinyi") {
        XposedLogClient.configure(authority = authority, source = source)
    }

    fun installXposedRuntimeLogSink() {
        CoreLogSinkHolder.install(object : CoreLogSink {
            override fun append(
                priority: Int,
                tag: String,
                message: String,
                force: Boolean,
                route: String?,
                sensitive: Boolean,
                throwableText: String?,
            ) {
                XposedLogClient.append(priority, tag, message, force, route, sensitive, throwableText)
            }
        })
    }

    fun bindRuntimeLogContext(
        context: Context,
        verboseLogging: Boolean,
    ) {
        XposedLogClient.attachContext(context)
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
}
