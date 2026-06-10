package io.github.magisk317.relay.android.common.utils

import io.github.magisk317.smscode.runtime.contract.logging.LogRoute

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
    fun v(route: LogRoute, message: String, vararg args: Any?) {
        RelayLogger.v(route, message, *args)
    }

    @JvmStatic
    fun d(route: LogRoute, message: String, vararg args: Any?) {
        RelayLogger.d(route, message, *args)
    }

    @JvmStatic
    fun i(route: LogRoute, message: String, vararg args: Any?) {
        RelayLogger.i(route, message, *args)
    }

    @JvmStatic
    fun w(route: LogRoute, message: String, vararg args: Any?) {
        RelayLogger.w(route, message, *args)
    }

    @JvmStatic
    fun e(route: LogRoute, message: String, vararg args: Any?) {
        RelayLogger.e(route, message, *args)
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
