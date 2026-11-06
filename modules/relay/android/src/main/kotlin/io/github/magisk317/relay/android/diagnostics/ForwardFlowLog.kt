package io.github.magisk317.relay.android.diagnostics

import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.logging.LogLevel
import io.github.magisk317.xposed.logging.MagiskLogPolicy

/**
 * Application-side forwarding flow logs.
 * Available only in internal diagnostic builds.
 */
object ForwardFlowLog {
    private const val TAG = "ForwardFlow"
    private val BUILD_LOGGING_ENABLED = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD
    @Volatile
    private var testSink: ((Int, String) -> Unit)? = null

    fun d(traceId: String?, message: String) {
        write(LogLevel.DEBUG.priority, traceId, message)
    }

    fun i(traceId: String?, message: String) {
        write(LogLevel.INFO.priority, traceId, message)
    }

    fun w(traceId: String?, message: String) {
        write(LogLevel.WARN.priority, traceId, message)
    }

    fun e(traceId: String?, message: String, throwable: Throwable? = null) {
        if (!BUILD_LOGGING_ENABLED && testSink == null) return
        if (!MagiskLogPolicy.current().loggingEnabled && testSink == null) return
        val finalMessage = if (throwable == null) {
            message
        } else {
            "$message exception=${throwable.javaClass.simpleName}"
        }
        write(LogLevel.ERROR.priority, traceId, finalMessage)
    }

    private fun write(priority: Int, traceId: String?, message: String) {
        if (!BUILD_LOGGING_ENABLED && testSink == null) return
        if (!MagiskLogPolicy.current().loggingEnabled && testSink == null) return
        val prefixed = if (traceId.isNullOrBlank()) {
            message
        } else {
            "[trace=$traceId] $message"
        }
        val safeMessage = DefaultLogSanitizer.sanitizeIfEnabled(prefixed)
        testSink?.let { sink ->
            sink(priority, safeMessage)
            return
        }
        RuntimeDiagnosticsBridge.ensureInstalled()
        RuntimeLogStore.append(
            priority = priority,
            tag = TAG,
            message = safeMessage,
            force = true,
            route = RuntimeLogStore.ROUTE_FORWARD,
        )
    }

    fun setTestSink(sink: ((Int, String) -> Unit)?) {
        testSink = sink
    }
}
