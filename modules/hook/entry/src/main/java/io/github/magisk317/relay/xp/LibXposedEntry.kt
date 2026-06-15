package io.github.magisk317.relay.xp

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.util.concurrent.ConcurrentHashMap
import io.github.magisk317.relay.android.platform.clipboard.AndroidClipboardPlatformBridge
import io.github.magisk317.relay.android.platform.notification.AndroidNotificationPlatformBridge
import io.github.magisk317.relay.android.platform.sms.AndroidSmsRuntimeBridge
import io.github.magisk317.relay.android.platform.xpbridge.AndroidXpDiagnosticsBridge
import io.github.magisk317.relay.android.platform.xpbridge.AndroidXpPrefsBridge
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.platform.xpbridge.RuntimeXpAppConfigBridge
import io.github.magisk317.relay.platform.xpbridge.RuntimeXpRecordBridge
import io.github.magisk317.relay.platform.xpbridge.RuntimeXpSmsDispatchBridge
import io.github.magisk317.relay.xp.hook.code.SmsHandlerHook
import io.github.magisk317.relay.xp.hook.forward.SmsForwardHook
import io.github.magisk317.relay.xp.hook.me.ModuleUtilsHook
import io.github.magisk317.relay.xp.hook.mms.MmsMessagesHook
import io.github.magisk317.relay.xp.hook.keepalive.KeepAliveHook
import io.github.magisk317.relay.xp.hook.telephony.SmsProviderHook
import io.github.magisk317.relay.xp.runtime.RuntimeBridgeFactory
import io.github.magisk317.relay.xpbridge.XpAppConfigFacade
import io.github.magisk317.relay.xpbridge.XpClipboard
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import io.github.magisk317.relay.xpbridge.XpHookDiagnostics
import io.github.magisk317.relay.xpbridge.XpNotificationBridge
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpRecordFacade
import io.github.magisk317.relay.xpbridge.XpSmsRuntimeBridge
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
        private const val MIN_LIBXPOSED_API_VERSION = 102
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
        KeepAliveHook(),
    )

    private var processName: String = "unknown"
    private val loadedPackages = ConcurrentHashMap<String, ClassLoader>()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        val api = apiVersion
        if (api < MIN_LIBXPOSED_API_VERSION) {
            Log.w(BuildConfig.LOG_TAG, "LibXposedEntry skipped: apiVersion=$api < $MIN_LIBXPOSED_API_VERSION")
            return
        }
        Log.i(BuildConfig.LOG_TAG, "LibXposedEntry running API 102 path: apiVersion=$api")
        installCoreRuntime()
        XpHookDiagnostics.installRuntimeBridge(AndroidXpDiagnosticsBridge)
        XpHookDiagnostics.installXposedRuntimeLogSink()
        XpAppConfigFacade.installRuntimeBridge(RuntimeXpAppConfigBridge)
        XpRecordFacade.installRuntimeBridge(RuntimeXpRecordBridge)
        XpDispatchCoordinator.installRuntimeBridge(RuntimeXpSmsDispatchBridge)
        XpClipboard.installPlatformBridge(AndroidClipboardPlatformBridge)
        XpNotificationBridge.installPlatformBridge(AndroidNotificationPlatformBridge)
        XpSmsRuntimeBridge.installPlatformBridge(AndroidSmsRuntimeBridge)
        XpPrefs.installPlatformBridge(AndroidXpPrefsBridge)
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
        loadedPackages["android"] = param.classLoader
        val loadParam = LoadParam("android", processName, param.classLoader)
        dispatchLoad(loadParam)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        loadedPackages[param.packageName] = param.classLoader
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
            packageName == "com.xiaomi.phone" ||
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

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        param.setSavedInstanceState(Pair(processName, HashMap(loadedPackages)))
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun onHotReloaded(param: HotReloadedParam) {
        installCoreRuntime()
        val hookApi = HookEnv.api as? LibXposedHookApi ?: return
        hookApi.beginHotReload(param.oldHookHandles)
        
        val state = param.savedInstanceState as? Pair<String, Map<String, ClassLoader>>
        if (state != null) {
            processName = state.first
            state.second.forEach { (pkg, cl) ->
                dispatchLoad(LoadParam(pkg, processName, cl))
            }
        }
        
        val removed = hookApi.finishHotReload()
        Log.i(BuildConfig.LOG_TAG, "onHotReloaded: replaced hooks, removed $removed stale hooks")
    }
}
