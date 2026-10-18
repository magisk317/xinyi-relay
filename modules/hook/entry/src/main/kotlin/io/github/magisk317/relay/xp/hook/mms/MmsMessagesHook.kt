package io.github.magisk317.relay.xp.hook.mms

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xp.hook.code.CodeWorker
import io.github.magisk317.relay.xp.hook.code.SmsBlacklistHitRecorder
import io.github.magisk317.relay.xp.hook.code.SmsBlockEvaluator
import io.github.magisk317.relay.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.relay.xp.hook.helper.XinyiHookHostCapabilities
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.smscode.xposed.hook.HookHostCapabilities
import io.github.magisk317.smscode.xposed.hook.telephony.BlockEvaluation
import io.github.magisk317.smscode.xposed.hook.telephony.MmsEntryPointHook
import io.github.magisk317.smscode.xposed.hook.telephony.MmsEntryPointHookHost

/**
 * relay wiring for the shared MMS entry-point hook.
 *
 * relay owns an outbound forward pipeline, so unlike XSC it persists a blacklist-hit
 * row per blocked SMS and force-deletes blacklisted SMS from the inbox. Prefs,
 * escaping, parsing and heartbeat all come from the unified
 * [HookHostCapabilities] adapter instead of per-file xpbridge calls.
 */
private object XinyiMmsEntryPointHost : MmsEntryPointHookHost {
    override val applicationId: String = BuildConfig.APPLICATION_ID

    override val capabilities: HookHostCapabilities = XinyiHookHostCapabilities

    override fun shouldSuppressByRelay(context: Context, tag: String): Boolean =
        ModuleConflictArbiter.shouldSuppressByRelay(context, tag)

    override fun onPluginContextReady(
        pluginContext: Context,
        phoneContext: Context,
        source: String,
    ) {
        capabilities.onProcessInit(pluginContext)
        capabilities.recordHookHeartbeat(
            context = pluginContext,
            packageName = "com.android.mms",
            processName = phoneContext.applicationInfo?.processName ?: "com.android.mms",
            source = "mms_${source.substringAfterLast('.')}",
            verboseLogging = capabilities.isVerboseLogMode(pluginContext),
        )
    }

    override fun evaluateBlock(
        pluginContext: Context,
        intent: Intent,
        eventId: String,
        source: String,
    ): BlockEvaluation? {
        val result = SmsBlockEvaluator.evaluate(
            pluginContext = pluginContext,
            intent = intent,
            eventId = eventId,
            source = source,
        ) ?: return null
        return BlockEvaluation(
            smsMsg = result.smsMsg,
            blockReason = result.blockReason,
            blacklistDeleteOnly = result.blacklistDeleteOnly,
            blacklistResult = result.blacklistResult,
            decision = result.decision,
        )
    }

    override fun onBlacklistEvaluated(
        pluginContext: Context,
        evaluation: BlockEvaluation,
        eventId: String,
        source: String,
    ) {
        val smsMsg = evaluation.smsMsg as? SmsMsg ?: return
        val blacklistResult = evaluation.blacklistResult
            as? io.github.magisk317.smscode.verification.BlacklistMatchResult ?: return
        val decision = evaluation.decision
            as? io.github.magisk317.smscode.runtime.verification.SmsHandlerDispatchDecision.Decision
            ?: return
        SmsBlacklistHitRecorder.record(
            pluginContext = pluginContext,
            smsMsg = smsMsg,
            blacklistResult = blacklistResult,
            decision = decision,
            eventId = eventId,
            source = source,
        )
    }

    override fun deleteSmsFromInbox(
        pluginContext: Context,
        hostContext: Context,
        smsMsg: Any,
    ) {
        val msg = smsMsg as? SmsMsg ?: return
        OperateSmsAction(
            pluginContext,
            hostContext,
            msg,
            OperateSmsAction.FORCE_DELETE,
        ).call()
    }

    override fun runParseWorker(
        pluginContext: Context,
        phoneContext: Context,
        intent: Intent,
        eventId: String,
    ) {
        CodeWorker(pluginContext, phoneContext, intent, eventId).parse()
    }
}

class MmsMessagesHook : io.github.magisk317.xposed.BaseHook() {
    private val delegate = MmsEntryPointHook(XinyiMmsEntryPointHost)

    override fun hookOnLoadPackage(): Boolean = delegate.hookOnLoadPackage()

    override fun onLoadPackage(param: io.github.magisk317.xposed.LoadParam) {
        delegate.onLoadPackage(param)
    }

    override fun onHotReloading() {
        delegate.onHotReloading()
    }
}
