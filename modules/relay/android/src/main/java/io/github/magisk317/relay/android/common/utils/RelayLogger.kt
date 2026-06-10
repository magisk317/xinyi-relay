package io.github.magisk317.relay.android.common.utils

import android.util.Log
import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore
import io.github.magisk317.smscode.runtime.contract.logging.LogEvent
import io.github.magisk317.smscode.runtime.contract.logging.LogFormatter
import io.github.magisk317.smscode.runtime.contract.logging.LogLevel
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute

object RelayLogger {

    private val LOG_TAG = BuildConfig.LOG_TAG
    private const val LOG_TO_XPOSED = BuildConfig.LOG_TO_XPOSED

    @Volatile
    private var logLevel = BuildConfig.LOG_LEVEL

    @Volatile
    private var testSink: ((Int, String) -> Unit)? = null

    @Volatile
    private var runtimeSink: RuntimeSink = RuntimeLogStoreSink

    @JvmStatic
    fun v(message: String, vararg args: Any?) {
        log(Log.VERBOSE, message, *args)
    }

    @JvmStatic
    fun d(message: String, vararg args: Any?) {
        log(Log.DEBUG, message, *args)
    }

    @JvmStatic
    fun i(message: String, vararg args: Any?) {
        log(Log.INFO, message, *args)
    }

    @JvmStatic
    fun w(message: String, vararg args: Any?) {
        log(Log.WARN, message, *args)
    }

    @JvmStatic
    fun e(message: String, vararg args: Any?) {
        log(Log.ERROR, message, *args)
    }

    @JvmStatic
    fun v(route: LogRoute, message: String, vararg args: Any?) {
        logWithRoute(Log.VERBOSE, route, message, *args)
    }

    @JvmStatic
    fun d(route: LogRoute, message: String, vararg args: Any?) {
        logWithRoute(Log.DEBUG, route, message, *args)
    }

    @JvmStatic
    fun i(route: LogRoute, message: String, vararg args: Any?) {
        logWithRoute(Log.INFO, route, message, *args)
    }

    @JvmStatic
    fun w(route: LogRoute, message: String, vararg args: Any?) {
        logWithRoute(Log.WARN, route, message, *args)
    }

    @JvmStatic
    fun e(route: LogRoute, message: String, vararg args: Any?) {
        logWithRoute(Log.ERROR, route, message, *args)
    }

    fun log(priority: Int, message: String, vararg args: Any?) {
        log(priority, null, defaultForceFor(priority), true, message, *args)
    }

    fun log(
        priority: Int,
        route: LogRoute,
        force: Boolean,
        sensitive: Boolean,
        message: String,
        vararg args: Any?,
    ) {
        log(
            priority = priority,
            route = route.id,
            force = force,
            sensitive = sensitive,
            message = message,
            args = args,
        )
    }

    fun log(
        priority: Int,
        route: String?,
        force: Boolean,
        sensitive: Boolean,
        message: String,
        vararg args: Any?,
    ) {
        if (priority < logLevel) return

        val formattedMessage = LogFormatter.formatArgs(message, args)
        val logMessage = if (sensitive) {
            SensitiveLogPolicy.sanitizeLogMessage(formattedMessage)
        } else {
            formattedMessage
        }
        testSink?.let { sink ->
            sink(priority, logMessage)
            return
        }

        runCatching { Log.println(priority, LOG_TAG, logMessage) }

        if (LOG_TO_XPOSED) {
            runCatching { Log.println(priority, "LSPosed-Bridge", "$LOG_TAG: $logMessage") }
        }

        val resolvedRoute = route ?: RuntimeLogStore.routeFromCallerClassName(resolveCallerClassName())
        runtimeSink.append(
            priority = priority,
            tag = LOG_TAG,
            message = logMessage,
            force = force,
            route = resolvedRoute,
        )
    }

    fun setLogLevel(value: Int) {
        logLevel = value
    }

    fun getLogLevel(): Int = logLevel

    fun setTestSink(sink: ((Int, String) -> Unit)?) {
        testSink = sink
    }

    internal fun setRuntimeSinkForTest(sink: RuntimeSink?) {
        runtimeSink = sink ?: RuntimeLogStoreSink
    }

    internal fun formatMessageForTest(message: String, args: Array<out Any?>): String {
        return LogFormatter.formatArgs(message, args)
    }

    internal fun defaultForceFor(priority: Int): Boolean {
        return priority >= Log.INFO
    }

    private fun logWithRoute(priority: Int, route: LogRoute, message: String, vararg args: Any?) {
        log(priority, route, defaultForceFor(priority), true, message, *args)
    }

    private fun resolveCallerClassName(): String? {
        return Throwable().stackTrace
            .mapNotNull { it.className }
            .firstOrNull { className ->
                className != RelayLogger::class.java.name &&
                    !className.startsWith("${RelayLogger::class.java.name}\$") &&
                    className != XLog::class.java.name &&
                    !className.startsWith("${XLog::class.java.name}\$") &&
                    !className.startsWith("timber.log.")
            }
    }

    internal interface RuntimeSink {
        fun append(
            priority: Int,
            tag: String,
            message: String,
            force: Boolean,
            route: String?,
        )
    }

    private object RuntimeLogStoreSink : RuntimeSink {
        override fun append(
            priority: Int,
            tag: String,
            message: String,
            force: Boolean,
            route: String?,
        ) {
            RuntimeLogStore.append(
                LogEvent(
                    level = LogLevel.fromPriority(priority),
                    tag = tag,
                    message = message,
                    route = route,
                    force = force,
                    sensitive = false,
                ),
            )
        }
    }
}
