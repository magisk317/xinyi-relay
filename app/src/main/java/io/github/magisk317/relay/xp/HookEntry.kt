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
            XLog.w(
                "Diag build config: logTag=%s logLevel=%d logToXposed=%s moduleVersion=%d",
                BuildConfig.LOG_TAG,
                BuildConfig.LOG_LEVEL,
                BuildConfig.LOG_TO_XPOSED,
                BuildConfig.MODULE_VERSION,
            )
        } catch (t: Throwable) {
            XLog.e("", t)
        }
    }

    @Throws(Throwable::class)
    fun handleLoadPackage(
        lpparam: XC_LoadPackage.LoadPackageParam,
        source: String = "unknown",
    ) {
        val isSystemScope =
            lpparam.packageName == "android" ||
                lpparam.packageName == "system" ||
                lpparam.processName == "system_server" ||
                lpparam.processName == "android"
        XLog.d(
            "HookEntry: Loaded package: %s process: %s source=%s",
            lpparam.packageName,
            lpparam.processName,
            source,
        )
        if (isSystemScope) {
            XLog.w(
                "HookEntry: System scope package loaded: pkg=%s process=%s source=%s",
                lpparam.packageName,
                lpparam.processName,
                source,
            )
        }
        for (hook in mHookList) {
            if (!hook.hookOnLoadPackage()) continue
            val hookName = hook.javaClass.simpleName
            runCatching {
                if (isSystemScope) {
                    XLog.w(
                        "HookEntry: System scope hook begin: hook=%s pkg=%s process=%s source=%s",
                        hookName,
                        lpparam.packageName,
                        lpparam.processName,
                        source,
                    )
                }
                hook.onLoadPackage(lpparam)
                if (isSystemScope) {
                    XLog.w(
                        "HookEntry: System scope hook end: hook=%s pkg=%s process=%s source=%s",
                        hookName,
                        lpparam.packageName,
                        lpparam.processName,
                        source,
                    )
                }
            }.onFailure {
                XLog.e(
                    "HookEntry: hook failed: hook=%s pkg=%s process=%s source=%s err=%s",
                    hookName,
                    lpparam.packageName,
                    lpparam.processName,
                    source,
                    it.message ?: it.javaClass.simpleName,
                )
                XLog.e("", it)
            }
        }
    }
}
