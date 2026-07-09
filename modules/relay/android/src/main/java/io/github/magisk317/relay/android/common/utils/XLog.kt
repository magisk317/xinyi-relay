package io.github.magisk317.relay.android.common.utils

import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.runtime.contract.logging.XLog as ContractXLog

/**
 * Thin delegate to the unified XLog in smscode-core:contract.
 * Preserves `io.github.magisk317.relay.android.common.utils.XLog` import path.
 */
object XLog {
    @JvmStatic fun v(message: String, vararg args: Any?) = ContractXLog.v(message, *args)
    @JvmStatic fun d(message: String, vararg args: Any?) = ContractXLog.d(message, *args)
    @JvmStatic fun i(message: String, vararg args: Any?) = ContractXLog.i(message, *args)
    @JvmStatic fun w(message: String, vararg args: Any?) = ContractXLog.w(message, *args)
    @JvmStatic fun e(message: String, vararg args: Any?) = ContractXLog.e(message, *args)
    @JvmStatic fun v(route: LogRoute, message: String, vararg args: Any?) = ContractXLog.v(route, message, *args)
    @JvmStatic fun d(route: LogRoute, message: String, vararg args: Any?) = ContractXLog.d(route, message, *args)
    @JvmStatic fun i(route: LogRoute, message: String, vararg args: Any?) = ContractXLog.i(route, message, *args)
    @JvmStatic fun w(route: LogRoute, message: String, vararg args: Any?) = ContractXLog.w(route, message, *args)
    @JvmStatic fun e(route: LogRoute, message: String, vararg args: Any?) = ContractXLog.e(route, message, *args)
    @JvmStatic fun setLogLevel(logLevel: Int) = ContractXLog.setLogLevel(logLevel)
    @JvmStatic fun getLogLevel(): Int = ContractXLog.getLogLevel()
    @JvmStatic fun setTestSink(sink: ((Int, String) -> Unit)?) = ContractXLog.setTestSink(sink)
}
