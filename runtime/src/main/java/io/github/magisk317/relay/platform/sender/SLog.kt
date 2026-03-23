package io.github.magisk317.relay.platform.sender

import android.util.Log
import io.github.magisk317.relay.diagnostics.RuntimeLogStore

internal object SLog {
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        RuntimeLogStore.append(
            priority = Log.DEBUG,
            tag = tag,
            message = message,
            force = true,
            route = RuntimeLogStore.ROUTE_SENDER,
        )
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        RuntimeLogStore.append(
            priority = Log.INFO,
            tag = tag,
            message = message,
            force = true,
            route = RuntimeLogStore.ROUTE_SENDER,
        )
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        RuntimeLogStore.append(
            priority = Log.WARN,
            tag = tag,
            message = message,
            force = true,
            route = RuntimeLogStore.ROUTE_SENDER,
        )
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w(tag, message, throwable)
        RuntimeLogStore.append(
            Log.WARN,
            tag,
            "$message\n${Log.getStackTraceString(throwable)}",
            force = true,
            route = RuntimeLogStore.ROUTE_SENDER,
        )
    }

    fun e(tag: String, message: String) {
        Log.e(tag, message)
        RuntimeLogStore.append(
            priority = Log.ERROR,
            tag = tag,
            message = message,
            force = true,
            route = RuntimeLogStore.ROUTE_SENDER,
        )
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
        RuntimeLogStore.append(
            Log.ERROR,
            tag,
            "$message\n${Log.getStackTraceString(throwable)}",
            force = true,
            route = RuntimeLogStore.ROUTE_SENDER,
        )
    }
}
