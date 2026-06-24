package io.github.magisk317.relay.xp

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
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
import io.github.magisk317.smscode.xposed.hook.notification.NotificationManagerHook
import io.github.magisk317.smscode.xposed.hook.permission.PermissionGranterHook
import io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.smscode.xposed.runtime.CoreRuntimeAccess
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.BaseLibXposedEntry
import io.github.magisk317.xposed.LibXposedHookApi
import io.github.magisk317.xposed.LoadParam

class LibXposedEntry : BaseLibXposedEntry {

    @Suppress("unused", "UnusedParameter")
    constructor(xposed: XposedInterface, loadedParam: ModuleLoadedParam) : super(xposed, loadedParam)
    constructor() : super()

    override val logTag: String = BuildConfig.LOG_TAG

    override val hooks: List<BaseHook> = listOf(
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

    override fun installModuleRuntime(module: XposedModule, hookApi: LibXposedHookApi) {
        installCoreRuntime()
        XpHookDiagnostics.installRuntimeBridge(AndroidXpDiagnosticsBridge)
        XpHookDiagnostics.configureLogClient(
            authority = "${BuildConfig.APPLICATION_ID}.xposed.log",
            source = "Relay",
        )
        XpHookDiagnostics.installXposedRuntimeLogSink()
        XpAppConfigFacade.installRuntimeBridge(RuntimeXpAppConfigBridge)
        XpRecordFacade.installRuntimeBridge(RuntimeXpRecordBridge)
        XpDispatchCoordinator.installRuntimeBridge(RuntimeXpSmsDispatchBridge)
        XpClipboard.installPlatformBridge(AndroidClipboardPlatformBridge)
        XpNotificationBridge.installPlatformBridge(AndroidNotificationPlatformBridge)
        XpSmsRuntimeBridge.installPlatformBridge(AndroidSmsRuntimeBridge)
        XpPrefs.installPlatformBridge(AndroidXpPrefsBridge)
        HookEnv.init(hookApi)
        XpPrefs.installRuntimeBridge(RuntimeBridgeFactory.create(module))
        try {
            XLog.setLogLevel(BuildConfig.LOG_LEVEL)
        } catch (t: Throwable) {
            XLog.e("", t)
        }
    }

    override fun postDispatch(loadParam: LoadParam) {
        HookTargetDiagnostics.logPackageReadyProbeIfVerbose(loadParam)
        HookTargetDiagnostics.logInboundSmsClassProbeAtInfo(loadParam)
        if (isCriticalHookTarget(loadParam.packageName)) {
            val message = "LibXposedEntry package ready: pkg=${loadParam.packageName} process=${loadParam.processName}"
            Log.w(BuildConfig.LOG_TAG, message)
            Log.w("LSPosed-Bridge", "${BuildConfig.LOG_TAG}: $message")
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
}
