package io.github.magisk317.relay.android.common.utils

import android.util.Log
import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore
import timber.log.Timber

object XLog {

    private val LOG_TAG = BuildConfig.LOG_TAG

    @Volatile
    private var sLogLevel = BuildConfig.LOG_LEVEL
    @Volatile
    private var testSink: ((Int, String) -> Unit)? = null
    private const val LOG_TO_XPOSED = BuildConfig.LOG_TO_XPOSED

    private fun log(priority: Int, message: String, vararg args: Any?) {
        if (priority < sLogLevel) return

        val logMessage = formatMessageForTest(message, args)
        testSink?.let { sink ->
            sink(priority, logMessage)
            return
        }

        Log.println(priority, LOG_TAG, logMessage)

        if (LOG_TO_XPOSED) {
            Log.println(priority, "LSPosed-Bridge", "$LOG_TAG: $logMessage")
        }

        val route = RuntimeLogStore.routeFromCallerClassName(resolveCallerClassName())
        RuntimeLogStore.append(
            priority = priority,
            tag = LOG_TAG,
            message = logMessage,
            force = true,
            route = route,
        )
        Timber.log(priority, message, *args)
    }

    private fun resolveCallerClassName(): String? {
        return Throwable().stackTrace
            .mapNotNull { it.className }
            .firstOrNull { className ->
                className != XLog::class.java.name &&
                    !className.startsWith("${XLog::class.java.name}\$") &&
                    !className.startsWith("timber.log.")
            }
    }

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
    fun setLogLevel(logLevel: Int) {
        sLogLevel = logLevel
    }

    @JvmStatic
    fun getLogLevel(): Int = sLogLevel

    @JvmStatic
    fun setTestSink(sink: ((Int, String) -> Unit)?) {
        testSink = sink
    }

    @JvmStatic
    internal fun formatMessageForTest(
        message: String,
        args: Array<out Any?>,
    ): String {
        val lastArg = args.lastOrNull()
        return if (lastArg is Throwable) {
            message + '\n' + Log.getStackTraceString(lastArg)
        } else {
            if (args.isNotEmpty()) {
                try {
                    String.format(message, *args)
                } catch (ignored: Exception) {
                    message
                }
            } else {
                message
            }
        }
    }
}
