package com.github.magisk317.smscode.common.utils

import android.util.Log

/**
 * Application-side forwarding flow logs.
 * Always persisted to RuntimeLogStore for troubleshooting, independent of verbose mode.
 */
object ForwardFlowLog {
    private const val TAG = "ForwardFlow"

    fun d(traceId: String?, message: String) {
        write(Log.DEBUG, traceId, message)
    }

    fun i(traceId: String?, message: String) {
        write(Log.INFO, traceId, message)
    }

    fun w(traceId: String?, message: String) {
        write(Log.WARN, traceId, message)
    }

    fun e(traceId: String?, message: String, throwable: Throwable? = null) {
        val finalMessage = if (throwable == null) {
            message
        } else {
            "$message\n${Log.getStackTraceString(throwable)}"
        }
        write(Log.ERROR, traceId, finalMessage)
    }

    private fun write(priority: Int, traceId: String?, message: String) {
        val prefixed = if (traceId.isNullOrBlank()) {
            message
        } else {
            "[trace=$traceId] $message"
        }
        Log.println(priority, TAG, prefixed)
        RuntimeLogStore.append(priority, TAG, prefixed, force = true)
    }
}
