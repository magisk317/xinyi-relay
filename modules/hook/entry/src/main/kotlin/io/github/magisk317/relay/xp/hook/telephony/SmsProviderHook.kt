package io.github.magisk317.relay.xp.hook.telephony

import android.content.Context
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.xp.HookTargetDiagnostics
import io.github.magisk317.relay.xpbridge.XpHookDiagnostics
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.xposed.hook.telephony.BaseSmsProviderHook
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderHookHost
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderHookInstaller
import io.github.magisk317.xposed.LoadParam

/**
 * Host wiring for the shared telephony provider hook.
 *
 * Installation, package filtering and write diagnostics live in
 * `BaseSmsProviderHook`; this only bridges relay's xpbridge diagnostics and prefs.
 */
private object XinyiSmsProviderHookHost : SmsProviderHookHost {
    override val applicationId: String = BuildConfig.APPLICATION_ID

    override fun recordHeartbeat(
        pluginContext: Context,
        phoneContext: Context,
        processName: String,
        source: String,
    ) {
        XpHookDiagnostics.recordSmsHookHeartbeat(
            context = pluginContext,
            packageName = SmsProviderHookInstaller.TARGET_PACKAGE,
            processName = processName,
            source = source,
            verboseLogging = XpPrefs.isVerboseLogMode(pluginContext),
        )
    }

    override fun isVerboseLogMode(pluginContext: Context): Boolean =
        XpPrefs.isVerboseLogMode(pluginContext)

    override fun onTargetProcessHit(param: LoadParam) {
        HookTargetDiagnostics.logTargetProcessHitIfVerbose(
            hookName = "SmsProviderHook",
            loadParam = param,
            targetPackage = SmsProviderHookInstaller.TARGET_PACKAGE,
        )
    }

    override fun onTargetClassMissing(param: LoadParam) {
        HookTargetDiagnostics.logTargetMissIfVerbose(
            hookName = "SmsProviderHook",
            loadParam = param,
            reason = "class_not_found",
            detail = SmsProviderHookInstaller.TARGET_CLASS,
        )
    }
}

class SmsProviderHook : BaseSmsProviderHook(XinyiSmsProviderHookHost)
