package io.github.magisk317.relay.diagnostics

import android.util.Log

/**
 * Application-side forwarding flow logs.
 * Always persisted to RuntimeLogStore for troubleshooting, independent of verbose mode.
 */
object ForwardFlowLog {
    private const val TAG = "ForwardFlow"
    @Volatile
    private var testSink: ((Int, String) -> Unit)? = null

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
        testSink?.let { sink ->
            sink(priority, prefixed)
            return
        }
        Log.println(priority, TAG, prefixed)
        RuntimeLogStore.append(
            priority = priority,
            tag = TAG,
            message = prefixed,
            force = true,
            route = RuntimeLogStore.ROUTE_FORWARD,
        )
    }

    fun setTestSink(sink: ((Int, String) -> Unit)?) {
        testSink = sink
    }
}
