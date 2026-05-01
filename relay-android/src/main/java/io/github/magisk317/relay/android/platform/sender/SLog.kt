package io.github.magisk317.relay.android.platform.sender

import android.util.Log
import io.github.magisk317.relay.android.common.utils.SensitiveLogPolicy
import io.github.magisk317.relay.android.diagnostics.RuntimeLogStore

internal object SLog {
    fun d(tag: String, message: String) {
        val safeMessage = SensitiveLogPolicy.sanitizeSenderLogMessage(message)
        Log.d(tag, safeMessage)
        RuntimeLogStore.append(
            priority = Log.DEBUG,
            tag = tag,
            message = safeMessage,
            force = true,
            route = RuntimeLogStore.ROUTE_SENDER,
        )
    }

    fun i(tag: String, message: String) {
        val safeMessage = SensitiveLogPolicy.sanitizeSenderLogMessage(message)
        Log.i(tag, safeMessage)
        RuntimeLogStore.append(
            priority = Log.INFO,
            tag = tag,
            message = safeMessage,
            force = true,
            route = RuntimeLogStore.ROUTE_SENDER,
        )
    }

    fun w(tag: String, message: String) {
        val safeMessage = SensitiveLogPolicy.sanitizeSenderLogMessage(message)
        Log.w(tag, safeMessage)
        RuntimeLogStore.append(
            priority = Log.WARN,
            tag = tag,
            message = safeMessage,
            force = true,
            route = RuntimeLogStore.ROUTE_SENDER,
        )
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        val safeMessage = SensitiveLogPolicy.sanitizeSenderLogMessage(message)
        Log.w(tag, safeMessage, throwable)
        RuntimeLogStore.append(
            Log.WARN,
            tag,
            "$safeMessage\n${Log.getStackTraceString(throwable)}",
            force = true,
            route = RuntimeLogStore.ROUTE_SENDER,
        )
    }

    fun e(tag: String, message: String) {
        val safeMessage = SensitiveLogPolicy.sanitizeSenderLogMessage(message)
        Log.e(tag, safeMessage)
        RuntimeLogStore.append(
            priority = Log.ERROR,
            tag = tag,
            message = safeMessage,
            force = true,
            route = RuntimeLogStore.ROUTE_SENDER,
        )
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        val safeMessage = SensitiveLogPolicy.sanitizeSenderLogMessage(message)
        Log.e(tag, safeMessage, throwable)
        RuntimeLogStore.append(
            Log.ERROR,
            tag,
            "$safeMessage\n${Log.getStackTraceString(throwable)}",
            force = true,
            route = RuntimeLogStore.ROUTE_SENDER,
        )
    }
}
