package com.github.magisk317.smscode.common.utils

import android.util.Log
import io.github.magisk317.xinyi.relay.storage.BuildConfig
import timber.log.Timber

object XLog {

    private val LOG_TAG = BuildConfig.LOG_TAG

    @Volatile
    private var sLogLevel = BuildConfig.LOG_LEVEL
    private const val LOG_TO_XPOSED = BuildConfig.LOG_TO_XPOSED

    private fun log(priority: Int, message: String, vararg args: Any?) {
        if (priority < sLogLevel) return

        // Write to the default log tag
        val lastArg = args.lastOrNull()
        val logMessage = if (lastArg is Throwable) {
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
        Log.println(priority, LOG_TAG, logMessage)

        // Duplicate to the Xposed log if enabled
        if (LOG_TO_XPOSED) {
            Log.println(priority, "LSPosed-Bridge", "$LOG_TAG: $logMessage")
        }

        RuntimeLogStore.append(priority, LOG_TAG, logMessage)
        Timber.log(priority, message, *args)
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
}
