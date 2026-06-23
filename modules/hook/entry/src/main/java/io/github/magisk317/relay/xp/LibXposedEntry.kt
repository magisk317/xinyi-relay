package io.github.magisk317.relay.xp

import android.os.Bundle
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.ref.WeakReference
import java.util.ArrayList
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
        private const val STATE_PROCESS_NAME = "processName"
        private const val STATE_LOADED_PACKAGES = "loadedPackages"
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
        installModuleRuntime(param, LibXposedHookApi(this))
        installInitZygoteHooks()
        // Dispatch hooks to already-running target processes (e.g. com.android.phone).
        // onPackageReady is NOT called for processes that were already running when
        // the module was loaded, so we must proactively dispatch here.
        dispatchCurrentLoadedTargets(param, phase = "moduleLoadedCurrentProcess")
    }

    private fun installModuleRuntime(param: ModuleLoadedParam, hookApi: LibXposedHookApi) {
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
        HookEnv.init(hookApi)
        XpPrefs.installRuntimeBridge(RuntimeBridgeFactory.create(this))
        processName = if (param.isSystemServer) "android" else param.processName

        try {
            XLog.setLogLevel(BuildConfig.LOG_LEVEL)
        } catch (t: Throwable) {
            XLog.e("", t)
        }
    }

    private fun installInitZygoteHooks() {
        for (hook in hookList) {
            if (hook.hookInitZygote()) {
                hook.initZygote(ZygoteParam())
            }
        }
    }

    /**
     * Dispatch hooks to already-running target processes when the module is first loaded.
     * onPackageReady is NOT called for processes that were already running, so we must
     * proactively dispatch here. This is critical for com.android.phone and com.xiaomi.phone
     * which are long-running system processes.
     */
    private fun dispatchCurrentLoadedTargets(param: ModuleLoadedParam, phase: String) {
        val process = if (param.isSystemServer) "android" else param.processName
        if (process == "android" || process == "system" || process == "system_server") {
            return // System server is handled by onSystemServerStarting
        }
        val packageName = when (process) {
            "com.android.phone", "com.xiaomi.phone", "com.android.providers.telephony" -> process
            "com.android.mms", "com.android.mms:mms_service" -> "com.android.mms"
            else -> return
        }
        val classLoader = resolveLoadedPackageClassLoader(packageName)
        if (classLoader != null) {
            loadedPackages.putIfAbsent(packageName, classLoader)
            dispatchLoad(LoadParam(packageName, processName, classLoader))
            XLog.i("LibXposedEntry: dispatched current loaded target pkg=%s process=%s phase=%s", packageName, process, phase)
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        loadedPackages["android"] = param.classLoader
        val loadParam = LoadParam("android", processName, param.classLoader)
        dispatchLoad(loadParam)
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader
        loadedPackages.putIfAbsent(param.packageName, classLoader)
        val loadParam = LoadParam(param.packageName, processName, classLoader)
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
        return runCatching {
            param.setSavedInstanceState(createHotReloadState())
            cleanupForHotReload()
            true
        }.getOrElse { t ->
            Log.e(BuildConfig.LOG_TAG, "LibXposedEntry hot reload rejected: ${t.message}", t)
            false
        }
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        val hookApi = LibXposedHookApi(this)
        installModuleRuntime(param, hookApi)
        hookApi.beginHotReload(param.oldHookHandles)
        val removed = try {
            installInitZygoteHooks()
            restoreHotReloadState(param.savedInstanceState)
            resolveCurrentProcessTargets(param).forEach { (pkg, cl) ->
                loadedPackages.putIfAbsent(pkg, cl)
            }
            loadedPackages.forEach { (pkg, cl) ->
                dispatchLoad(LoadParam(pkg, processName, cl))
            }
            hookApi.finishHotReload()
        } catch (t: Throwable) {
            hookApi.finishHotReload()
            Log.e(BuildConfig.LOG_TAG, "LibXposedEntry hot reload failed", t)
            throw t
        }
        Log.i(BuildConfig.LOG_TAG, "onHotReloaded: replaced hooks, removed $removed stale hooks")
    }

    private fun createHotReloadState(): Bundle {
        return Bundle().apply {
            putString(STATE_PROCESS_NAME, processName)
            putStringArrayList(STATE_LOADED_PACKAGES, ArrayList(loadedPackages.keys.sorted()))
        }
    }

    private fun cleanupForHotReload() {
        for (hook in hookList) {
            runCatching { hook.onHotReloading() }
                .onFailure { t ->
                    Log.e(BuildConfig.LOG_TAG, "Hot reload cleanup failed: ${hook.javaClass.name}", t)
                }
        }
    }

    private fun restoreHotReloadState(savedState: Any?) {
        val state = savedState as? Bundle ?: return
        processName = state.getString(STATE_PROCESS_NAME) ?: processName
        val packages = state.getStringArrayList(STATE_LOADED_PACKAGES) ?: return
        loadedPackages.clear()
        packages.forEach { pkg ->
            val classLoader = resolveLoadedPackageClassLoader(pkg)
            if (classLoader == null) {
                Log.w(BuildConfig.LOG_TAG, "Hot reload skipped package without classloader: $pkg")
            } else {
                loadedPackages[pkg] = classLoader
            }
        }
    }

    private fun resolveCurrentProcessTargets(param: ModuleLoadedParam): Map<String, ClassLoader> {
        val process = if (param.isSystemServer) "android" else param.processName
        return when (process) {
            "android", "system", "system_server" -> mapOf("android" to resolveSystemServerClassLoader())
            "com.android.phone", "com.xiaomi.phone" -> {
                val classLoader = resolveLoadedPackageClassLoader(process) ?: resolveContextClassLoader()
                mapOf(process to classLoader)
            }
            "com.android.mms", "com.android.mms:mms_service" -> {
                val classLoader = resolveLoadedPackageClassLoader("com.android.mms") ?: resolveContextClassLoader()
                mapOf("com.android.mms" to classLoader)
            }
            "com.android.providers.telephony" -> {
                val classLoader = resolveLoadedPackageClassLoader(process) ?: resolveContextClassLoader()
                mapOf(process to classLoader)
            }
            else -> emptyMap()
        }
    }

    private fun resolveLoadedPackageClassLoader(packageName: String): ClassLoader? {
        if (packageName == "android" || packageName == "system") {
            return resolveSystemServerClassLoader()
        }
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val activityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null) ?: return null
            listOf("mPackages", "mResourcePackages").firstNotNullOfOrNull { fieldName ->
                val field = activityThreadClass.getDeclaredField(fieldName).apply { isAccessible = true }
                val packages = field.get(activityThread) as? Map<*, *> ?: return@firstNotNullOfOrNull null
                val loadedApkRef = packages[packageName] ?: return@firstNotNullOfOrNull null
                val loadedApk = (loadedApkRef as? WeakReference<*>)?.get() ?: loadedApkRef
                loadedApk.javaClass
                    .getDeclaredMethod("getClassLoader")
                    .apply { isAccessible = true }
                    .invoke(loadedApk) as? ClassLoader
            }
        }.getOrElse { t ->
            Log.w(BuildConfig.LOG_TAG, "Hot reload classloader resolve failed for $packageName: ${t.message}", t)
            null
        }
    }

    private fun resolveSystemServerClassLoader(): ClassLoader {
        return resolveContextClassLoader()
    }

    private fun resolveContextClassLoader(): ClassLoader {
        return Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
    }
}
