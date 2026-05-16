package io.github.magisk317.relay.android.common.utils

import android.util.Log

object XLog {

    @JvmStatic
    fun v(message: String, vararg args: Any?) {
        RelayLogger.v(message, *args)
    }

    @JvmStatic
    fun d(message: String, vararg args: Any?) {
        RelayLogger.d(message, *args)
    }

    @JvmStatic
    fun i(message: String, vararg args: Any?) {
        RelayLogger.i(message, *args)
    }

    @JvmStatic
    fun w(message: String, vararg args: Any?) {
        RelayLogger.w(message, *args)
    }

    @JvmStatic
    fun e(message: String, vararg args: Any?) {
        RelayLogger.e(message, *args)
    }

    @JvmStatic
    fun setLogLevel(logLevel: Int) {
        RelayLogger.setLogLevel(logLevel)
    }

    @JvmStatic
    fun getLogLevel(): Int = RelayLogger.getLogLevel()

    @JvmStatic
    fun setTestSink(sink: ((Int, String) -> Unit)?) {
        RelayLogger.setTestSink(sink)
    }

    @JvmStatic
    internal fun formatMessageForTest(
        message: String,
        args: Array<out Any?>,
    ): String {
        return RelayLogger.formatMessageForTest(message, args)
    }
}
