package io.github.magisk317.relay.xp

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.xp.hook.code.SmsHandlerHook
import io.github.magisk317.relay.xp.hook.forward.SmsForwardHook
import io.github.magisk317.relay.xp.hook.me.ModuleUtilsHook
import io.github.magisk317.relay.xp.hook.mms.MmsMessagesHook
import io.github.magisk317.relay.xp.hook.telephony.SmsProviderHook
import io.github.magisk317.relay.xp.runtime.RuntimeBridgeFactory
import io.github.magisk317.relay.xpbridge.XpHookDiagnostics
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.xposed.hook.BaseHook
import io.github.magisk317.smscode.xposed.hook.notification.NotificationManagerHook
import io.github.magisk317.smscode.xposed.hook.permission.PermissionGranterHook
import io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook
import io.github.magisk317.smscode.xposed.hookapi.HookEnv
import io.github.magisk317.smscode.xposed.hookapi.LibXposedHookApi
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.ZygoteParam
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.smscode.xposed.runtime.CoreRuntimeAccess
import io.github.magisk317.smscode.xposed.utils.XLog

class LibXposedEntry : XposedModule {
    private companion object {
        private const val LIBXPOSED_API_VERSION = 101
    }

    @Suppress("unused", "UnusedParameter")
    constructor(xposed: XposedInterface, loadedParam: ModuleLoadedParam) : super()

    constructor() : super()

    private val hookList: List<BaseHook> = listOf(
        SmsHandlerHook(),
        MmsMessagesHook(),
        SmsForwardHook(),
        ModuleUtilsHook(),
        PermissionGranterHook(),
        SystemInputInjectorHook(),
        NotificationManagerHook(),
        SmsProviderHook(),
    )

    private var processName: String = "unknown"

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        val api = apiVersion
        if (api != LIBXPOSED_API_VERSION) {
            Log.w(BuildConfig.LOG_TAG, "LibXposedEntry skipped: apiVersion=$api")
            return
        }
        installCoreRuntime()
        XpHookDiagnostics.installXposedRuntimeLogSink()
        HookEnv.init(LibXposedHookApi(this))
        XpPrefs.installRuntimeBridge(RuntimeBridgeFactory.create(this))
        processName = if (param.isSystemServer) "android" else param.processName

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

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        val loadParam = LoadParam("android", processName, param.classLoader)
        dispatchLoad(loadParam)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        val loadParam = LoadParam(param.packageName, processName, param.classLoader)
        dispatchLoad(loadParam)
    }

    private fun dispatchLoad(loadParam: LoadParam) {
        installCoreRuntime()
        XLog.d("LibXposedEntry: Loaded package: ${loadParam.packageName} process: ${loadParam.processName}")
        HookTargetDiagnostics.logPackageReadyProbeIfVerbose(loadParam)
        HookTargetDiagnostics.logInboundSmsClassProbeAtInfo(loadParam)
        if (isCriticalHookTarget(loadParam.packageName)) {
            val message = "LibXposedEntry package ready: pkg=${loadParam.packageName} process=${loadParam.processName}"
            Log.w(BuildConfig.LOG_TAG, message)
            Log.w("LSPosed-Bridge", "${BuildConfig.LOG_TAG}: $message")
        }
        if ("android" == loadParam.packageName || "system" == loadParam.packageName) {
            XLog.w(
                "LibXposedEntry: Android/system package loaded: pkg=%s process=%s",
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

    private fun isCriticalHookTarget(packageName: String): Boolean {
        return packageName == "android" ||
            packageName == "system" ||
            packageName == "com.android.phone" ||
            packageName == "com.android.providers.telephony"
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
