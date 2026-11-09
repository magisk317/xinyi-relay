package io.github.magisk317.relay.xp.hook.telephony

import android.content.Context
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.xp.HookTargetDiagnostics
import io.github.magisk317.relay.xp.hook.helper.XinyiHookHostCapabilities
import io.github.magisk317.smscode.xposed.hook.telephony.BaseSmsProviderHook
import io.github.magisk317.smscode.xposed.hook.telephony.SmsHookCapabilities
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderDraft
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderHookHost
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderHookInstaller
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderWriteDecision
import io.github.magisk317.xposed.LoadParam

/**
 * Host wiring for the shared telephony provider hook.
 *
 * Installation, package filtering and write diagnostics live in
 * `BaseSmsProviderHook`; prefs/heartbeat defaults are backed by the unified
 * [HookHostCapabilities] adapter, so this file only carries relay-specific
 * target diagnostics plus the provider-level blacklist decision.
 */
private object XinyiSmsProviderHookHost : SmsProviderHookHost {
    override val applicationId: String = BuildConfig.APPLICATION_ID

    override val capabilities: SmsHookCapabilities = XinyiHookHostCapabilities

    override fun onInboundSmsProviderWrite(
        pluginContext: Context,
        phoneContext: Context,
        drafts: List<SmsProviderDraft>,
        call: SmsProviderHookInstaller.WriteCall,
    ): SmsProviderWriteDecision = ProviderSmsBlacklistGuard.evaluate(pluginContext, drafts)

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
