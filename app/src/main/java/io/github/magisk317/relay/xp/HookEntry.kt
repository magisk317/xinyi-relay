package io.github.magisk317.relay.xp

import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.xp.hook.google.GoogleMessagesHook
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.xp.hook.BaseHook
import io.github.magisk317.relay.xp.hook.code.SmsHandlerHook
import io.github.magisk317.relay.xp.hook.me.ModuleUtilsHook
import io.github.magisk317.relay.xp.hook.permission.PermissionGranterHook
import io.github.magisk317.relay.xp.hook.system.SystemInputInjectorHook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry :
    IXposedHookLoadPackage,
    IXposedHookZygoteInit {

    private val mHookList: List<BaseHook> = listOf(
        SmsHandlerHook(), // InBoundsSmsHandler Hook
        GoogleMessagesHook(), // Google Messages read sync hook
        ModuleUtilsHook(), // ModuleUtils Hook
        PermissionGranterHook(), // PackageManagerService Hook
        SystemInputInjectorHook(), // System Server Input Injection Hook
        io.github.magisk317.relay.xp.hook.notification.NotificationManagerHook(), // Notification Intercept Hook
    )

    @Throws(Throwable::class)
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        for (hook in mHookList) {
            if (hook.hookInitZygote()) {
                hook.initZygote(startupParam)
            }
        }

        try {
            XLog.setLogLevel(BuildConfig.LOG_LEVEL)
        } catch (t: Throwable) {
            XLog.e("", t)
        }
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        XLog.d("HookEntry: Loaded package: ${lpparam.packageName} process: ${lpparam.processName}")
        if ("android" == lpparam.packageName || "system" == lpparam.packageName) {
            XLog.w(
                "HookEntry: Android/system package loaded: pkg=%s process=%s",
                lpparam.packageName,
                lpparam.processName,
            )
        }
        for (hook in mHookList) {
            if (hook.hookOnLoadPackage()) {
                hook.onLoadPackage(lpparam)
            }
        }
    }
}
