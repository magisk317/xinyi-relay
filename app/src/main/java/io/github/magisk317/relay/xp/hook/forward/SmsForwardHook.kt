package io.github.magisk317.relay.xp.hook.forward

import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Telephony
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.xp.HookTargetDiagnostics
import io.github.magisk317.relay.xp.hook.SmsForwardConvergence
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.relay.xpbridge.XpSharedRuntimeGate
import io.github.magisk317.relay.xp.hook.SmsHookDispatchGate
import io.github.magisk317.relay.xp.hook.SmsHookRuntimeSession
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xp.helper.SmsCodeConflictNoticeHelper
import io.github.magisk317.smscode.domain.utils.RecentEventDeduplicator
import io.github.magisk317.smscode.domain.utils.SmsForwardDedupKeyFactory
import io.github.magisk317.smscode.domain.utils.SmsForwardDedupSpec
import io.github.magisk317.smscode.verification.SmsIntentHookSupport
import io.github.magisk317.smscode.xposed.helper.XposedWrapper
import io.github.magisk317.smscode.xposed.hook.BaseHook
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.runBlocking

/**
 * Dedicated SMS forward hook (decoupled from SMS code pipeline).
 */
class SmsForwardHook : BaseHook() {
    private data class IncomingSmsDispatch(
        val intent: Intent,
        val action: String,
        val eventId: String,
        val pluginContext: Context,
        val phoneContext: Context,
    )

    private data class PreparedForwardDispatch(
        val dispatch: IncomingSmsDispatch,
        val dedupKey: String,
        val prepared: io.github.magisk317.relay.xpbridge.PreparedSmsHookDispatch,
    )

    private val runtimeSession = SmsHookRuntimeSession(SMSCODE_PACKAGE, ANDROID_PHONE_PACKAGE)
    @Volatile
    private var suppressionLogged = false

    override fun onLoadPackage(lpparam: LoadParam) {
        if (ANDROID_PHONE_PACKAGE != lpparam.packageName) return
        XLog.i("SmsForwardHook initializing")
        val classLoader = lpparam.classLoader ?: run {
            XLog.w("SmsForwardHook skipped: classLoader is null for %s", lpparam.packageName)
            return
        }
        try {
            hookConstructor(classLoader)
            hookDispatchIntent(classLoader)
        } catch (t: Throwable) {
            XLog.e("SmsForwardHook init failed", t)
        }
    }

    private fun hookConstructor(classLoader: ClassLoader) {
        XLog.i("SmsForwardHook: Hooking InboundSmsHandler constructor")
        val smsHandlerClazz = XposedWrapper.findClass(SMS_HANDLER_CLASS, classLoader) ?: return
        XposedWrapper.hookAllConstructors(smsHandlerClazz, ConstructorHook())
    }

    private fun hookDispatchIntent(classLoader: ClassLoader) {
        XLog.d("SmsForwardHook: Hooking dispatchIntent()")
        val inboundSmsHandlerClass = XposedWrapper.findClass(SMS_HANDLER_CLASS, classLoader) ?: run {
            XLog.e("SmsForwardHook: Class not found: %s", SMS_HANDLER_CLASS)
            return
        }
        val exactMethod = inboundSmsHandlerClass.declaredMethods.firstOrNull { it.name == DISPATCH_INTENT_METHOD }
        if (exactMethod == null) {
            XLog.e("SmsForwardHook: Method not found: %s in %s", DISPATCH_INTENT_METHOD, SMS_HANDLER_CLASS)
            return
        }
        XposedWrapper.hookMethod(exactMethod, DispatchIntentHook())
    }

    private inner class ConstructorHook : MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            runCatching { afterConstructorHandler(param) }
                .onFailure { XLog.e("SmsForwardHook constructor hook failed", it) }
        }
    }

    private fun afterConstructorHandler(param: MethodHookParam) {
        val context = param.args.getOrNull(1) as? Context ?: return
        HookTargetDiagnostics.logInboundSmsRuntimeHitAtInfo(
            source = "SmsForwardHook#constructor",
            packageName = context.packageName,
            processName = context.applicationInfo?.processName ?: context.packageName,
        )
        val runtime = runtimeSession.initialize(context)
        if (runtime == null) {
            XLog.e("SmsForwardHook: plugin context is null after creation attempt")
            return
        }
        SmsCodeConflictNoticeHelper.initNotificationChannel(runtime.pluginContext, runtime.phoneContext)
        runtimeSession.recordHeartbeat("sms_forward_constructor")
        if (ModuleConflictArbiter.shouldSuppressByRelay(runtime.phoneContext, "SmsForwardHook#constructor")) {
            logSuppressedOnce("constructor")
        }
    }

    private inner class DispatchIntentHook : MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            runCatching { beforeDispatchIntentHandler(param) }
                .onFailure { XLog.e("SmsForwardHook dispatchIntent hook failed", it) }
        }
    }

    private fun beforeDispatchIntentHandler(param: MethodHookParam) {
        val dispatch = resolveIncomingSmsDispatch(param) ?: return
        if (shouldSkipDispatch(dispatch)) return
        val preparedDispatch = prepareForwardDispatch(dispatch) ?: return
        if (shouldSuppressDuplicateDispatch(preparedDispatch)) return
        dispatchPreparedForward(preparedDispatch)
    }

    private fun resolveIncomingSmsDispatch(param: MethodHookParam): IncomingSmsDispatch? {
        val intent = param.args.getOrNull(0) as? Intent ?: return null
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_DELIVER_ACTION &&
            action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        ) {
            return null
        }
        val eventId = XpDispatchCoordinator.ensureIncomingEventId(intent)
        if (SmsForwardConvergence.wasParsedSmsForwardDispatched(intent)) {
            XLog.i(
                "SmsForwardHook skipped: parsed sms forward already dispatched. event_id=%s",
                eventId,
            )
            return null
        }
        val runtime = runtimeSession.recordHeartbeat("sms_forward_dispatch") ?: run {
            XLog.e(
                "SmsForwardHook: Context is null, skip. pluginContext=%s phoneContext=%s",
                null,
                null,
            )
            return null
        }
        HookTargetDiagnostics.logInboundSmsRuntimeHitAtInfo(
            source = "SmsForwardHook#dispatchIntent",
            packageName = runtime.phoneContext.packageName,
            processName = runtime.phoneContext.applicationInfo?.processName ?: runtime.phoneContext.packageName,
            detail = "event_id=$eventId action=$action",
        )
        val hadSimRouting = SmsForwardSimRoutingResolver.readFromIntent(intent).hasValue()
        val resolvedRouting = SmsForwardSimRoutingResolver.ensureSimRoutingExtras(
            intent = intent,
            handler = param.thisObject,
            args = param.args,
        )
        if (!hadSimRouting && resolvedRouting?.hasValue() == true) {
            XLog.i(
                "SmsForwardHook inferred sim routing: event_id=%s sim_slot=%s sub_id=%s",
                eventId,
                resolvedRouting.simSlot?.toString() ?: "<none>",
                resolvedRouting.subId?.toString() ?: "<none>",
            )
        } else if (!hadSimRouting) {
            XLog.w(
                "SmsForwardHook sim routing unresolved: event_id=%s detail=%s",
                eventId,
                SmsForwardSimRoutingResolver.debugSnapshot(param.thisObject, param.args),
            )
        }
        return IncomingSmsDispatch(
            intent = intent,
            action = action,
            eventId = eventId,
            pluginContext = runtime.pluginContext,
            phoneContext = runtime.phoneContext,
        )
    }

    private fun shouldSkipDispatch(dispatch: IncomingSmsDispatch): Boolean {
        if (SmsIntentHookSupport.markDispatchHandled(dispatch.intent, dispatch.action, DISPATCH_HANDLER_KEY)) {
            XLog.w(
                "SmsForwardHook duplicate sms suppressed: event_id=%s action=%s source=intent_extra",
                dispatch.eventId,
                dispatch.action,
            )
            return true
        }

        return when (
            SmsHookDispatchGate.evaluate(
                moduleEnabled = XpPrefs.isEnabled(dispatch.pluginContext),
                relayFeatureRequired = true,
                relayFeaturesEnabled = XpPrefs.relayFeaturesEnabled(dispatch.pluginContext),
                suppressedByRelay = ModuleConflictArbiter.shouldSuppressByRelay(
                    dispatch.phoneContext,
                    "SmsForwardHook#dispatchIntent",
                ),
            ).reason
        ) {
            SmsHookDispatchGate.BlockReason.MODULE_DISABLED -> {
                XLog.w("SmsForwardHook: module disabled, skip forward. event_id=%s", dispatch.eventId)
                true
            }

            SmsHookDispatchGate.BlockReason.RELAY_DISABLED -> {
                XLog.w("SmsForwardHook: relay disabled, skip forward. event_id=%s", dispatch.eventId)
                true
            }

            SmsHookDispatchGate.BlockReason.CONFLICT_SUPPRESSED -> {
                logSuppressedOnce("dispatchIntent")
                SmsCodeConflictNoticeHelper.notifyConflictOnSms(
                    dispatch.pluginContext,
                    dispatch.phoneContext,
                    dispatch.eventId,
                    "SmsForwardHook#dispatchIntent",
                )
                true
            }

            else -> false
        }
    }

    private fun prepareForwardDispatch(dispatch: IncomingSmsDispatch): PreparedForwardDispatch? {
        val smsMsg = XpDispatchCoordinator.parseIncomingSms(dispatch.intent) ?: run {
            XLog.w("SmsForwardHook: parse sms failed, skip. event_id=%s", dispatch.eventId)
            return null
        }
        if (smsMsg.sender.isNullOrBlank() || smsMsg.body.isNullOrBlank()) {
            XLog.w("SmsForwardHook: empty sender/body, skip. event_id=%s", dispatch.eventId)
            return null
        }

        val prepared = runBlocking {
            XpDispatchCoordinator.prepareIngressSms(
                pluginContext = dispatch.pluginContext,
                phoneContext = dispatch.phoneContext,
                smsMsg = smsMsg,
                sourceIntent = dispatch.intent,
                eventId = dispatch.eventId,
            )
        } ?: run {
            XLog.w(
                "SmsForwardHook: empty sender/body after ingress adapter, skip. event_id=%s",
                dispatch.eventId,
            )
            return null
        }
        val messageType = prepared.messageType ?: run {
            XLog.w("SmsForwardHook: ingress message type missing, skip. event_id=%s", dispatch.eventId)
            return null
        }
        if (!XpPrefs.isMessageTypeEnabled(dispatch.pluginContext, messageType)) {
            XLog.w(
                "SmsForwardHook: message type disabled, skip. event_id=%s type=%s",
                dispatch.eventId,
                messageType.name.lowercase(),
            )
            return null
        }

        val resolvedSmsMsg = prepared.smsMsg
        return PreparedForwardDispatch(
            dispatch = dispatch,
            dedupKey = buildSmsDispatchDedupKey(
                sender = resolvedSmsMsg.sender,
                body = resolvedSmsMsg.body,
                timestamp = resolvedSmsMsg.date,
            ),
            prepared = prepared,
        )
    }

    private fun shouldSuppressDuplicateDispatch(preparedDispatch: PreparedForwardDispatch): Boolean {
        val dispatch = preparedDispatch.dispatch
        val sharedDedupClaim = XpSharedRuntimeGate.claimWithinWindow(
            context = dispatch.pluginContext,
            fileName = DISPATCH_DEDUP_FILE_NAME,
            key = preparedDispatch.dedupKey,
            windowMs = SMS_FORWARD_DEDUP_WINDOW_MS,
            maxEntries = MAX_DISPATCH_DEDUP_ENTRIES,
        )
        if (!sharedDedupClaim.claimed) {
            XLog.w(
                "SmsForwardHook duplicate sms suppressed: event_id=%s action=%s key=%s source=shared_store ageMs=%d",
                dispatch.eventId,
                dispatch.action,
                preparedDispatch.dedupKey,
                sharedDedupClaim.ageMs ?: -1L,
            )
            return true
        }
        if (recentSmsForward.shouldDrop(preparedDispatch.dedupKey)) {
            XLog.w(
                "SmsForwardHook duplicate sms suppressed: event_id=%s action=%s key=%s source=memory",
                dispatch.eventId,
                dispatch.action,
                preparedDispatch.dedupKey,
            )
            return true
        }
        return false
    }

    private fun dispatchPreparedForward(preparedDispatch: PreparedForwardDispatch) {
        val dispatch = preparedDispatch.dispatch
        val dispatchResult = XpDispatchCoordinator.dispatchPreparedSms(
            context = dispatch.pluginContext,
            prepared = preparedDispatch.prepared,
            sentFromUid = Process.myUid(),
        )
        if (!dispatchResult.dispatched) {
            XLog.e(
                "SmsForwardHook: IPC token empty, skip forward. event_id=%s",
                dispatch.eventId,
            )
            return
        }
        if (dispatchResult.bypassUsed) {
            XLog.w(
                "SmsForwardHook: IPC token empty, continue with receiver-side bypass. event_id=%s uid=%d",
                dispatch.eventId,
                Process.myUid(),
            )
        }
        XLog.i(
            "SmsForwardHook forwarded: event_id=%s code_present=%s tokenPresent=%s",
            dispatch.eventId,
            preparedDispatch.prepared.smsMsg.smsCode?.isNotBlank() == true,
            dispatchResult.tokenPresent,
        )
    }

    private fun buildSmsDispatchDedupKey(
        sender: String?,
        body: String?,
        timestamp: Long,
    ): String {
        return SmsForwardDedupKeyFactory.build(
            SmsForwardDedupSpec(
                eventId = "",
                sender = sender,
                body = body,
                timestamp = timestamp,
                msgType = SMS_MSG_TYPE,
                source = SMS_HOOK_SOURCE,
            ),
        )
    }

    private fun logSuppressedOnce(stage: String) {
        if (suppressionLogged) return
        synchronized(this) {
            if (suppressionLogged) return
            XLog.w(
                "SmsForwardHook suppressed: reason=%s stage=%s package=%s",
                ModuleConflictArbiter.SUPPRESSION_REASON,
                stage,
                ModuleConflictArbiter.TARGET_RELAY_PACKAGE,
            )
            suppressionLogged = true
        }
    }

    companion object {
        private const val ANDROID_PHONE_PACKAGE = "com.android.phone"
        private const val TELEPHONY_PACKAGE = "com.android.internal.telephony"
        private const val SMS_HANDLER_CLASS = "$TELEPHONY_PACKAGE.InboundSmsHandler"
        private const val DISPATCH_HANDLER_KEY = "sms_forward"
        private val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private const val DISPATCH_INTENT_METHOD = "dispatchIntent"
        private const val SMS_MSG_TYPE = "sms"
        private const val SMS_HOOK_SOURCE = "sms_hook"
        private const val SMS_FORWARD_DEDUP_WINDOW_MS = 10_000L
        private const val DISPATCH_DEDUP_FILE_NAME = "sms_forward_dispatch_dedup"
        private const val MAX_DISPATCH_DEDUP_ENTRIES = 256
        private val recentSmsForward = RecentEventDeduplicator(windowMs = SMS_FORWARD_DEDUP_WINDOW_MS)
    }
}
