package io.github.magisk317.relay.xp.hook.forward

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Telephony
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.common.utils.ActivationDiagnosticsStore
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.common.utils.RuntimeLogStore
import io.github.magisk317.relay.common.utils.SmsCodeUtils
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.platform.ipc.ForwardBroadcastContract
import io.github.magisk317.relay.platform.ipc.ForwardReceiverIntentFactory
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xp.helper.SmsCodeConflictNoticeHelper
import io.github.magisk317.smscode.core.helper.XposedWrapper
import io.github.magisk317.smscode.core.hook.BaseHook
import io.github.magisk317.smscode.core.hookapi.LoadParam
import io.github.magisk317.smscode.core.hookapi.MethodHook
import io.github.magisk317.smscode.core.hookapi.MethodHookParam
import io.github.magisk317.smscode.core.utils.XLog
import kotlinx.coroutines.runBlocking
import kotlin.math.abs

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
        val eventId = ensureEventId(intent)
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

        val smsMsg = runCatching { SmsMsg.fromIntent(intent) }.getOrNull()
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

        val smsCode = runBlocking { SmsCodeUtils.parseSmsCodeIfExists(pluginContext, body) }
        val messageType = if (smsCode.isNullOrBlank()) MessageType.SMS_PLAIN else MessageType.SMS_CODE
        if (!PrefsReader.isMessageTypeEnabled(pluginContext, messageType)) {
            XLog.w(
                "SmsForwardHook: message type disabled, skip. event_id=%s type=%s",
                eventId,
                messageType.name.lowercase(),
            )
            return
        }

        val (company, packageName) = resolveCompanyAndPackage(phoneContext, body, smsCode)
        val resolvedDate = if (smsMsg.date > 0L) smsMsg.date else System.currentTimeMillis()
        val forwardIntent = ForwardReceiverIntentFactory.newHostIntent(pluginContext).apply {
            ForwardBroadcastContract.populatePayload(
                intent = this,
                sender = sender,
                body = body,
                date = resolvedDate,
                company = company,
                smsCode = smsCode,
                packageName = packageName,
                notifyChannelId = "",
                msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
                forwardSource = ForwardBroadcastContract.SOURCE_SMS_HOOK,
                eventId = eventId,
            )
        }
        ForwardBroadcastContract.copySimRoutingExtras(intent, forwardIntent)

        val token = PrefsReader.getIpcToken(pluginContext)
        if (token.isBlank()) {
            if (!shouldAllowSmsTokenBypass()) {
                XLog.e(
                    "SmsForwardHook: IPC token empty, skip forward. event_id=%s",
                    eventId,
                )
                return
            }
            XLog.w(
                "SmsForwardHook: IPC token empty, continue with receiver-side bypass. event_id=%s uid=%d sdk=%d",
                eventId,
                Process.myUid(),
                Build.VERSION.SDK_INT,
            )
        } else {
            ForwardBroadcastContract.putIpcToken(forwardIntent, token)
        }

        pluginContext.sendBroadcast(forwardIntent)
        XLog.i(
            "SmsForwardHook forwarded: event_id=%s code_present=%s tokenPresent=%s",
            eventId,
            smsCode.isNotBlank(),
            token.isNotBlank(),
        )
    }

    private fun resolveCompanyAndPackage(
        context: Context,
        body: String,
        smsCode: String?,
    ): Pair<String?, String?> {
        if (smsCode.isNullOrBlank()) return "" to null
        val candidates = SmsCodeUtils.parseCompanyCandidates(body)
            .map { it.trim().trim('【', '】', '[', ']') }
            .filter { it.isNotBlank() }
        var company = SmsCodeUtils.parseCompany(body).trim().trim('【', '】', '[', ']')
        var resolvedPackage: String? = null
        for (candidate in candidates) {
            val pkg = SmsCodeUtils.findPackageNameByLabel(context, candidate)
            if (!pkg.isNullOrBlank()) {
                company = candidate
                resolvedPackage = pkg
                break
            }
        }
        if (resolvedPackage.isNullOrBlank()) {
            resolvedPackage = SmsCodeUtils.findPackageNameByLabel(context, company)
        }
        return company to resolvedPackage
    }

    private fun shouldAllowSmsTokenBypass(): Boolean {
        val uid = Process.myUid()
        return uid == Process.SYSTEM_UID || uid == Process.PHONE_UID
    }

    private fun ensureEventId(intent: Intent): String {
        val existing = intent.getStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID).orEmpty().trim()
        if (existing.isNotEmpty()) {
            return existing
        }
        val generated = ForwardBroadcastContract.buildEventId("sms", abs(intent.hashCode()).toString(36))
        intent.putExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, generated)
        return generated
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
