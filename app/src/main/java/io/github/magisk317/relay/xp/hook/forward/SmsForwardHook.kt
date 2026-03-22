package io.github.magisk317.relay.xp.hook.forward

import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Telephony
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.common.utils.ActivationDiagnosticsStore
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.common.utils.RuntimeLogStore
import io.github.magisk317.relay.platform.ipc.SmsHookDispatchCoordinator
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xp.helper.SmsCodeConflictNoticeHelper
import io.github.magisk317.smscode.core.helper.XposedWrapper
import io.github.magisk317.smscode.core.hook.BaseHook
import io.github.magisk317.smscode.core.hookapi.LoadParam
import io.github.magisk317.smscode.core.hookapi.MethodHook
import io.github.magisk317.smscode.core.hookapi.MethodHookParam
import io.github.magisk317.smscode.core.utils.XLog
import kotlinx.coroutines.runBlocking

/**
 * Dedicated SMS forward hook (decoupled from SMS code pipeline).
 */
class SmsForwardHook : BaseHook() {
    private var mPhoneContext: Context? = null
    private var mPluginContext: Context? = null
    @Volatile
    private var suppressionLogged = false

    override fun onLoadPackage(lpparam: LoadParam) {
        if (ANDROID_PHONE_PACKAGE != lpparam.packageName) return
        XLog.i("SmsForwardHook initializing")
        try {
            hookConstructor(lpparam.classLoader)
            hookDispatchIntent(lpparam.classLoader)
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
        if (mPhoneContext != null) return
        mPhoneContext = context
        mPluginContext = runCatching {
            context.createPackageContext(
                SMSCODE_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY,
            )
        }.getOrNull()
        if (mPluginContext == null) {
            XLog.e("SmsForwardHook: plugin context is null after creation attempt")
        } else {
            val pluginContext = mPluginContext ?: return
            SmsCodeConflictNoticeHelper.initNotificationChannel(pluginContext, context)
            ActivationDiagnosticsStore.recordHookHeartbeat(
                context = pluginContext,
                packageName = ANDROID_PHONE_PACKAGE,
                processName = context.applicationInfo?.processName ?: ANDROID_PHONE_PACKAGE,
                source = "sms_forward_constructor",
                verboseLogging = PrefsReader.isVerboseLogMode(pluginContext),
                route = RuntimeLogStore.ROUTE_SMS_HOOK,
            )
            if (ModuleConflictArbiter.shouldSuppressByRelay(mPhoneContext, "SmsForwardHook#constructor")) {
                logSuppressedOnce("constructor")
            }
        }
    }

    private inner class DispatchIntentHook : MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            runCatching { beforeDispatchIntentHandler(param) }
                .onFailure { XLog.e("SmsForwardHook dispatchIntent hook failed", it) }
        }
    }

    private fun beforeDispatchIntentHandler(param: MethodHookParam) {
        val intent = param.args.getOrNull(0) as? Intent ?: return
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_DELIVER_ACTION &&
            action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        ) {
            return
        }
        val eventId = SmsHookDispatchCoordinator.ensureIncomingEventId(intent)
        val pluginContext = getPluginContext()
        val phoneContext = mPhoneContext
        if (pluginContext == null || phoneContext == null) {
            XLog.e(
                "SmsForwardHook: Context is null, skip. pluginContext=%s phoneContext=%s",
                pluginContext,
                phoneContext,
            )
            return
        }
        ActivationDiagnosticsStore.recordHookHeartbeat(
            context = pluginContext,
            packageName = ANDROID_PHONE_PACKAGE,
            processName = phoneContext.applicationInfo?.processName ?: ANDROID_PHONE_PACKAGE,
            source = "sms_forward_dispatch",
            verboseLogging = PrefsReader.isVerboseLogMode(pluginContext),
            route = RuntimeLogStore.ROUTE_SMS_HOOK,
        )
        if (!PrefsReader.isEnabled(pluginContext)) {
            XLog.w("SmsForwardHook: module disabled, skip forward. event_id=%s", eventId)
            return
        }
        if (!PrefsReader.relayFeaturesEnabled(pluginContext)) {
            XLog.w("SmsForwardHook: relay disabled, skip forward. event_id=%s", eventId)
            return
        }
        if (ModuleConflictArbiter.shouldSuppressByRelay(phoneContext, "SmsForwardHook#dispatchIntent")) {
            logSuppressedOnce("dispatchIntent")
            SmsCodeConflictNoticeHelper.notifyConflictOnSms(
                pluginContext,
                phoneContext,
                eventId,
                "SmsForwardHook#dispatchIntent",
            )
            return
        }

        val smsMsg = SmsHookDispatchCoordinator.parseIncomingSms(intent)
        if (smsMsg == null) {
            XLog.w("SmsForwardHook: parse sms failed, skip. event_id=%s", eventId)
            return
        }
        val sender = smsMsg.sender
        val body = smsMsg.body
        if (sender.isNullOrBlank() || body.isNullOrBlank()) {
            XLog.w("SmsForwardHook: empty sender/body, skip. event_id=%s", eventId)
            return
        }

        val prepared = runBlocking {
            SmsHookDispatchCoordinator.prepareIngressSms(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                sourceIntent = intent,
                eventId = eventId,
            )
        } ?: run {
            XLog.w("SmsForwardHook: empty sender/body after ingress adapter, skip. event_id=%s", eventId)
            return
        }
        val messageType = prepared.messageType ?: run {
            XLog.w("SmsForwardHook: ingress message type missing, skip. event_id=%s", eventId)
            return
        }
        if (!PrefsReader.isMessageTypeEnabled(pluginContext, messageType)) {
            XLog.w(
                "SmsForwardHook: message type disabled, skip. event_id=%s type=%s",
                eventId,
                messageType.name.lowercase(),
            )
            return
        }
        val resolvedSmsMsg = prepared.smsMsg

        val dispatchResult = SmsHookDispatchCoordinator.dispatchPreparedSms(
            context = pluginContext,
            prepared = prepared,
            sentFromUid = Process.myUid(),
        )
        if (!dispatchResult.dispatched) {
            XLog.e(
                "SmsForwardHook: IPC token empty, skip forward. event_id=%s",
                eventId,
            )
            return
        }
        if (dispatchResult.bypassUsed) {
            XLog.w(
                "SmsForwardHook: IPC token empty, continue with receiver-side bypass. event_id=%s uid=%d",
                eventId,
                Process.myUid(),
            )
        }
        XLog.i(
            "SmsForwardHook forwarded: event_id=%s code_present=%s tokenPresent=%s",
            eventId,
            resolvedSmsMsg.smsCode?.isNotBlank() == true,
            dispatchResult.tokenPresent,
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

    private fun getPluginContext(): Context? {
        if (mPluginContext == null) {
            mPluginContext = runCatching {
                mPhoneContext?.createPackageContext(
                    SMSCODE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
            }.getOrNull()
        }
        return mPluginContext
    }

    companion object {
        private const val ANDROID_PHONE_PACKAGE = "com.android.phone"
        private const val TELEPHONY_PACKAGE = "com.android.internal.telephony"
        private const val SMS_HANDLER_CLASS = "$TELEPHONY_PACKAGE.InboundSmsHandler"
        private val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private const val DISPATCH_INTENT_METHOD = "dispatchIntent"
    }
}
