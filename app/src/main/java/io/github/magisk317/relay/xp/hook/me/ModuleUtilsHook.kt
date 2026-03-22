package io.github.magisk317.relay.xp.hook.me

import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.smscode.xposed.utils.ModuleUtils
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.smscode.xposed.helper.XposedWrapper
import io.github.magisk317.smscode.xposed.hook.BaseHook
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam

/**
 * Hook class ModuleUtils
 */
class ModuleUtilsHook : BaseHook() {
    @Throws(Throwable::class)
    override fun onLoadPackage(lpparam: LoadParam) {
        if (SMSCODE_PACKAGE == lpparam.packageName) {
            try {
                XLog.i("Hooking current Xposed module status...")
                hookModuleUtils(lpparam)
            } catch (e: Throwable) {
                XLog.e("Failed to hook current Xposed module status.")
            }
        }
    }

    @Throws(Throwable::class)
    private fun hookModuleUtils(lpparam: LoadParam) {
        val className = ModuleUtils::class.java.name
        XposedWrapper.findAndHookMethod(
            className,
            lpparam.classLoader,
            "getModuleVersion",
            object : MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = MODULE_VERSION
                }
            },
        )
    }

    companion object {
        private const val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private const val MODULE_VERSION = BuildConfig.MODULE_VERSION
    }
}
