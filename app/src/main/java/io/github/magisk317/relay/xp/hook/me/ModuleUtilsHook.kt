package io.github.magisk317.relay.xp.hook.me

import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.common.utils.ModuleUtils
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.xp.hook.BaseHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Hook class ModuleUtils
 */
class ModuleUtilsHook : BaseHook() {
    @Throws(Throwable::class)
    override fun onLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
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
    private fun hookModuleUtils(lpparam: XC_LoadPackage.LoadPackageParam) {
        val className = ModuleUtils::class.java.name
        XposedHelpers.findAndHookMethod(
            className,
            lpparam.classLoader,
            "getModuleVersion",
            XC_MethodReplacement.returnConstant(MODULE_VERSION),
        )
    }

    companion object {
        private const val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private const val MODULE_VERSION = BuildConfig.MODULE_VERSION
    }
}
