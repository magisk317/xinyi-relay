package io.github.magisk317.relay.android.common.utils

import android.util.Log
import io.github.magisk317.relay.android.BuildConfig
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.xposed.logging.XLog as ContractXLog

/**
 * Thin delegate to the unified XLog in magisk-xposed-kit.
 * Preserves `io.github.magisk317.relay.android.common.utils.XLog` import path,
 * and keeps the [LogRoute] typed overloads by converting the route to its
 * neutral string id at the call site.
 */
object XLog {
    @JvmStatic fun configure() = ContractXLog.configure(
        tag = BuildConfig.LOG_TAG,
        logLevel = BuildConfig.LOG_LEVEL,
        logToXposed = BuildConfig.LOG_TO_XPOSED,
    )
    @JvmStatic fun v(message: String, vararg args: Any?) = ContractXLog.v(message, *args)
    @JvmStatic fun d(message: String, vararg args: Any?) = ContractXLog.d(message, *args)
    @JvmStatic fun i(message: String, vararg args: Any?) = ContractXLog.i(message, *args)
    @JvmStatic fun w(message: String, vararg args: Any?) = ContractXLog.w(message, *args)
    @JvmStatic fun e(message: String, vararg args: Any?) = ContractXLog.e(message, *args)
    @JvmStatic fun v(route: LogRoute, message: String, vararg args: Any?) =
        ContractXLog.log(Log.VERBOSE, route.id, false, true, message, *args)
    @JvmStatic fun d(route: LogRoute, message: String, vararg args: Any?) =
        ContractXLog.log(Log.DEBUG, route.id, false, true, message, *args)
    @JvmStatic fun i(route: LogRoute, message: String, vararg args: Any?) =
        ContractXLog.log(Log.INFO, route.id, false, true, message, *args)
    @JvmStatic fun w(route: LogRoute, message: String, vararg args: Any?) =
        ContractXLog.log(Log.WARN, route.id, true, true, message, *args)
    @JvmStatic fun e(route: LogRoute, message: String, vararg args: Any?) =
        ContractXLog.log(Log.ERROR, route.id, true, true, message, *args)
    @JvmStatic fun setLogLevel(logLevel: Int) = ContractXLog.setLogLevel(logLevel)
    @JvmStatic fun getLogLevel(): Int = ContractXLog.getLogLevel()
    @JvmStatic fun setTestSink(sink: ((Int, String) -> Unit)?) = ContractXLog.setTestSink(sink)
}
