package io.github.magisk317.relay.xp

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.xp.hook.code.SmsHandlerHook
import io.github.magisk317.relay.xp.hook.forward.SmsForwardHook
import io.github.magisk317.relay.xp.hook.me.ModuleUtilsHook
import io.github.magisk317.relay.xp.hook.telephony.SmsProviderHook
import io.github.magisk317.relay.xp.hookapi.LegacyHookApi
import io.github.magisk317.relay.xp.runtime.RuntimeBridgeFactory
import io.github.magisk317.relay.xpbridge.XpHookDiagnostics
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.xposed.hook.BaseHook
import io.github.magisk317.smscode.xposed.hook.notification.NotificationManagerHook
import io.github.magisk317.smscode.xposed.hook.permission.PermissionGranterHook
import io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook
import io.github.magisk317.smscode.xposed.hookapi.HookEnv
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.ZygoteParam
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.smscode.xposed.runtime.CoreRuntimeAccess
import io.github.magisk317.smscode.xposed.utils.XLog

class HookEntry :
    IXposedHookLoadPackage,
    IXposedHookZygoteInit {

    private val hookList: List<BaseHook> = listOf(
        SmsHandlerHook(),
        SmsForwardHook(),
        ModuleUtilsHook(),
        PermissionGranterHook(),
        SystemInputInjectorHook(),
        NotificationManagerHook(),
        SmsProviderHook(),
    )

    @Throws(Throwable::class)
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        installCoreRuntime()
        XpHookDiagnostics.installXposedRuntimeLogSink()
        HookEnv.init(LegacyHookApi())
        XpPrefs.installRuntimeBridge(RuntimeBridgeFactory.create())
        for (hook in hookList) {
            if (hook.hookInitZygote()) {
                hook.initZygote(ZygoteParam())
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
        installCoreRuntime()
        XpHookDiagnostics.installXposedRuntimeLogSink()
        HookEnv.init(LegacyHookApi())
        XpPrefs.installRuntimeBridge(RuntimeBridgeFactory.create())
        val loadParam = LoadParam(lpparam.packageName, lpparam.processName, lpparam.classLoader)
        XLog.d("HookEntry: Loaded package: ${loadParam.packageName} process: ${loadParam.processName}")
        if ("android" == loadParam.packageName || "system" == loadParam.packageName) {
            XLog.w(
                "HookEntry: Android/system package loaded: pkg=%s process=%s",
                loadParam.packageName,
                loadParam.processName,
            )
        }
        for (hook in hookList) {
            if (hook.hookOnLoadPackage()) {
                hook.onLoadPackage(loadParam)
            }
        }
    }

    private fun installCoreRuntime() {
        CoreRuntime.install(object : CoreRuntimeAccess {
            override val logTag: String = BuildConfig.LOG_TAG
            override val logLevel: Int = BuildConfig.LOG_LEVEL
            override val logToXposed: Boolean = BuildConfig.LOG_TO_XPOSED
            override val debug: Boolean = BuildConfig.DEBUG
            override val applicationId: String = BuildConfig.APPLICATION_ID
            override val actionNamespace: String = "io.github.magisk317.relay"
        })
    }
}
