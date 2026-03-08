package io.github.magisk317.relay.xp

import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.xp.hook.google.GoogleMessagesHook
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.xp.hook.BaseHook
import io.github.magisk317.relay.xp.hook.code.SmsHandlerHook
import io.github.magisk317.relay.xp.hook.permission.PermissionGranterHook
import io.github.magisk317.relay.xp.hook.system.SystemInputInjectorHook
import io.github.magisk317.relay.xp.runtime.RuntimeBridgeFactory
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.xp.compat.IXposedHookZygoteInit
import io.github.magisk317.relay.xp.compat.callbacks.XC_LoadPackage

class HookEntry {

    private val mHookList: List<BaseHook> = listOf(
        SmsHandlerHook(), // InBoundsSmsHandler Hook
        GoogleMessagesHook(), // Google Messages read sync hook
        PermissionGranterHook(), // PackageManagerService Hook
        SystemInputInjectorHook(), // System Server Input Injection Hook
        io.github.magisk317.relay.xp.hook.notification.NotificationManagerHook(), // Notification Intercept Hook
    )

    @Throws(Throwable::class)
    fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam, runtimeHandle: Any? = null) {
        PrefsReader.installRuntimeBridge(RuntimeBridgeFactory.create(runtimeHandle))

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
    fun handleLoadPackage(
        lpparam: XC_LoadPackage.LoadPackageParam,
        source: String = "unknown",
    ) {
        XLog.d(
            "HookEntry: Loaded package: %s process: %s source=%s",
            lpparam.packageName,
            lpparam.processName,
            source,
        )
        if ("android" == lpparam.packageName || "system" == lpparam.packageName) {
            XLog.w(
                "HookEntry: Android/system package loaded: pkg=%s process=%s source=%s",
                lpparam.packageName,
                lpparam.processName,
                source,
            )
        }
        for (hook in mHookList) {
            if (hook.hookOnLoadPackage()) {
                hook.onLoadPackage(lpparam)
            }
        }
    }
}
