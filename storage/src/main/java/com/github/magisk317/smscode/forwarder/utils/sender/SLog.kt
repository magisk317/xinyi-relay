package com.github.magisk317.smscode.forwarder.utils.sender

import android.util.Log
import com.github.magisk317.smscode.common.utils.RuntimeLogStore

internal object SLog {
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        RuntimeLogStore.append(Log.DEBUG, tag, message, force = true)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        RuntimeLogStore.append(Log.INFO, tag, message, force = true)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        RuntimeLogStore.append(Log.WARN, tag, message, force = true)
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w(tag, message, throwable)
        RuntimeLogStore.append(
            Log.WARN,
            tag,
            "$message\n${Log.getStackTraceString(throwable)}",
            force = true,
        )
    }

    fun e(tag: String, message: String) {
        Log.e(tag, message)
        RuntimeLogStore.append(Log.ERROR, tag, message, force = true)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
        RuntimeLogStore.append(
            Log.ERROR,
            tag,
            "$message\n${Log.getStackTraceString(throwable)}",
            force = true,
        )
    }
}
