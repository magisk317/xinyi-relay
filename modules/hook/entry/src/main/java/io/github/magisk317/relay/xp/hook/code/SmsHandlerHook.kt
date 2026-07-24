package io.github.magisk317.relay.xp.hook.code

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.xp.HookTargetDiagnostics
import io.github.magisk317.smscode.xposed.utils.ModuleActivationStore
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import io.github.magisk317.smscode.verification.SmsDispatchChainBlockDeduplicator
import io.github.magisk317.smscode.verification.SmsDispatchIntentDeduplicator
import io.github.magisk317.smscode.verification.SmsIntentHookSupport
import io.github.magisk317.smscode.xposed.hook.telephony.InboundSmsBlocker
import io.github.magisk317.relay.xp.hook.PhoneHookTargetPackages
import io.github.magisk317.relay.xp.hook.SmsHookRuntimeContext
import io.github.magisk317.relay.xp.hook.SmsHookRuntimeSession
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xp.helper.SmsCodeConflictNoticeHelper
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.relay.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import java.io.File
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.Executors
/**
 * Hook class com.android.internal.telephony.InboundSmsHandler
 */
class SmsHandlerHook : BaseHook() {
    private val smsOperationExecutor = Executors.newSingleThreadExecutor()
    private val runtimeSession = SmsHookRuntimeSession(SMSCODE_PACKAGE)
    private val inboundSmsBlocker = InboundSmsBlocker(SMS_HANDLER_CLASS)
    private val parsedCodeSmsForwarder = ParsedCodeSmsForwarder()
    private val constructorInitializer = SmsHookConstructorInitializer(
        runtimeInitializer = runtimeSession::initialize,
        heartbeatRecorder = { source -> runtimeSession.recordHeartbeat(source) },
        suppressionLogger = ::logSuppressedOnce,
        inboxObserverRegistrar = ::registerSmsInboxObserver,
    )
    private val dispatchIntentHandler = SmsDispatchIntentHandler(
        runtimeResolver = runtimeSession::recordHeartbeat,
        suppressionLogger = ::logSuppressedOnce,
        blacklistDeleteScheduler = ::scheduleBlacklistDelete,
        inboundBlocker = { inboundSmsHandler, receiver, reason, eventId ->
            inboundSmsBlocker.blockInboundSms(
                inboundSmsHandler = inboundSmsHandler,
                smsReceiver = receiver,
                reason = reason,
                eventId = eventId,
            )
        },
    )
    private var smsInboxObserver: SmsInboxObserver? = null
    @Volatile
    private var suppressionLogged = false

    override fun onLoadPackage(param: LoadParam) {
        XLog.withRoute(LogRoute.SMS_HOOK) {
            onLoadPackageRouted(param)
        }
    }

    private fun onLoadPackageRouted(lpparam: LoadParam) {
        if (PhoneHookTargetPackages.contains(lpparam.packageName)) {
            HookTargetDiagnostics.logTargetProcessHitIfVerbose(
                hookName = "SmsHandlerHook",
                loadParam = lpparam,
                targetPackage = lpparam.packageName,
            )
            XLog.i("SmsCode initializing")
            printDeviceInfo()
            try {
                hookSmsHandler(lpparam)
                emitHandler(
                    result = "ok",
                    reason = "installed",
                    stage = "hook_install",
                )
            } catch (e: Throwable) {
                XLog.e("Failed to hook SmsHandler", e)
                emitHandler(
                    result = "error",
                    reason = "install_failed",
                    stage = "hook_install",
                    statusOk = false,
                    errorClass = e.javaClass.simpleName,
                )
            }
            XLog.i("SmsCode initialize completely")
        }
    }

    private fun printDeviceInfo() {
        XLog.i("Phone manufacturer: %s", Build.MANUFACTURER)
        XLog.i("Phone model: %s", Build.MODEL)
        XLog.i("Android version: %s", Build.VERSION.RELEASE)
        val xposedVersion = resolveXposedVersion()
        if (xposedVersion != null) {
            XLog.i("Xposed bridge version: %d", xposedVersion)
        } else {
            XLog.i("Xposed bridge version: unknown")
        }
        XLog.i("SmsCode version: %s (%d)", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    }

    private fun resolveXposedVersion(): Int? {
        return HookEnv.api.getFrameworkVersionCode()?.toInt() ?: HookEnv.api.getApiVersion()
    }

    private fun hookSmsHandler(lpparam: LoadParam) {
        val classLoader = lpparam.classLoader
        hookConstructor(lpparam, classLoader)
        hookDispatchIntent(lpparam, classLoader)
        hookSmsDispatcherChain(classLoader)
    }

    private fun hookConstructor(lpparam: LoadParam, classLoader: ClassLoader) {
        // minSdkVersion 35: Only hook for Android 14+ / 15+
        hookConstructor34(lpparam, classLoader)
    }

    // Android 14+
    private fun hookConstructor34(lpparam: LoadParam, classLoader: ClassLoader) {
        XLog.i("Hooking InboundSmsHandler constructor for android v34+")
        val smsHandlerClazz = runCatching { HookHelpers.findClass(SMS_HANDLER_CLASS, classLoader) }.getOrNull()
        if (smsHandlerClazz != null) {
            HookEnv.api.hookAllConstructors(smsHandlerClazz, ConstructorHook())
        } else {
            HookTargetDiagnostics.logTargetMissIfVerbose(
                hookName = "SmsHandlerHook",
                loadParam = lpparam,
                reason = "class_not_found",
                detail = SMS_HANDLER_CLASS,
            )
        }
    }

    private fun hookDispatchIntent(lpparam: LoadParam, classLoader: ClassLoader) {
        // minSdkVersion 35: Only hook for Android 10+ / 15+
        hookDispatchIntent29(lpparam, classLoader)
    }

    private fun hookSmsDispatcherChain(classLoader: ClassLoader) {
        // Some ROMs/Android versions may dispatch SMS via alternative paths.
        hookDispatcherMethods(
            classLoader,
            SMS_HANDLER_CLASS,
            listOf(
                "dispatchSmsDeliveryIntent",
                "dispatchSmsDeliveryIntentToApp",
                "dispatchSmsDeliveryIntentToRegisteredReceivers",
            ),
        )
        hookDispatcherMethods(
            classLoader,
            "com.android.internal.telephony.SmsDispatchersController",
            listOf(
                "dispatchSmsDeliveryIntent",
                "dispatchSmsDeliveryIntentToApp",
                "dispatchSmsDeliveryIntentToRegisteredReceivers",
                "dispatchSmsDeliveryIntentToAppWithPermission",
            ),
        )
    }

    private fun hookDispatcherMethods(
        classLoader: ClassLoader,
        className: String,
        methodNames: List<String>,
    ) {
        val clazz = runCatching { HookHelpers.findClass(className, classLoader) }.getOrNull() ?: return
        methodNames.forEach { name ->
            val methods = clazz.declaredMethods.filter { it.name == name }
            if (methods.isEmpty()) return@forEach
            methods.forEach { method ->
                HookEnv.api.hookMethod(
                    method,
                    object : MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            XLog.withRoute(LogRoute.SMS_HOOK) {
                                try {
                                    maybeBlockFromDispatchChain(
                                        methodName = name,
                                        param = param,
                                        smsIntent = SmsIntentHookSupport.extractOrBuildSmsIntent(
                                            param.args,
                                            Telephony.Sms.Intents.SMS_DELIVER_ACTION,
                                        ),
                                    )
                                } catch (e: Throwable) {
                                    XLog.e("Error in dispatch chain hook $className.$name", e)
                                    // Do NOT re-throw: crashing here would break SMS delivery
                                }
                                val action = SmsIntentHookSupport.extractIntentAction(param.args)
                                XLog.w(
                                    "Diag SMS dispatch chain: class=%s method=%s action=%s args=%d",
                                    className,
                                    name,
                                    action ?: "<none>",
                                    param.args.size,
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    // Android 10+
    private fun hookDispatchIntent29(lpparam: LoadParam, classLoader: ClassLoader) {
        XLog.d("Hooking dispatchIntent() for Android v29+")
        val inboundSmsHandlerClass = runCatching { HookHelpers.findClass(SMS_HANDLER_CLASS, classLoader) }.getOrNull() ?: run {
            XLog.e("Class: %s cannot found", SMS_HANDLER_CLASS)
            return
        }

        val methods = inboundSmsHandlerClass.declaredMethods
        var exactMethod: Method? = null
        val dispatchIntentMethodName = "dispatchIntent"
        var receiverIndex = 0
        for (method in methods) {
            if (dispatchIntentMethodName == method.name) {
                exactMethod = method
                val parameterTypes = method.parameterTypes
                for (i in parameterTypes.indices) {
                    if (BroadcastReceiver::class.java.isAssignableFrom(parameterTypes[i])) {
                        receiverIndex = i
                    }
                }
                break
            }
        }

        exactMethod?.let {
            HookEnv.api.hookMethod(it, DispatchIntentHook(receiverIndex))
        } ?: run {
            XLog.e("Method %s for Class %s cannot found", dispatchIntentMethodName, SMS_HANDLER_CLASS)
            HookTargetDiagnostics.logTargetMissIfVerbose(
                hookName = "SmsHandlerHook",
                loadParam = lpparam,
                reason = "method_not_found",
                detail = "$SMS_HANDLER_CLASS#$dispatchIntentMethodName",
            )
        }
    }

    private inner class ConstructorHook : MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            XLog.withRoute(LogRoute.SMS_HOOK) {
                try {
                    afterConstructorHandler(param)
                } catch (e: Throwable) {
                    XLog.e("Error occurred in constructor hook", e)
                    // Do NOT re-throw: crashing here would kill the telephony process
                    // and break SMS reception on the device.
                }
            }
        }
    }

    private fun afterConstructorHandler(param: MethodHookParam) {
        val context = param.args.getOrNull(1) as? Context ?: return
        HookTargetDiagnostics.logInboundSmsRuntimeHitAtInfo(
            source = "SmsHandlerHook#constructor",
            packageName = context.packageName,
            processName = context.applicationInfo?.processName ?: context.packageName,
        )
        constructorInitializer.handle(context)
    }

    private fun registerSmsInboxObserver(runtime: SmsHookRuntimeContext) {
        if (smsInboxObserver != null) return
        smsInboxObserver = SmsInboxObserver(runtime.pluginContext, runtime.phoneContext).also { it.register() }
    }

    private inner class DispatchIntentHook(
        private val mReceiverIndex: Int,
    ) : MethodHook() {
        @Throws(Throwable::class)
        override fun beforeHookedMethod(param: MethodHookParam) {
            XLog.withRoute(LogRoute.SMS_HOOK) {
                try {
                    beforeDispatchIntentHandler(param, mReceiverIndex)
                } catch (e: Throwable) {
                    XLog.e("Error occurred in dispatchIntent() hook, ", e)
                }
            }
        }
    }

    private fun beforeDispatchIntentHandler(param: MethodHookParam, receiverIndex: Int) {
        val intent = param.args.getOrNull(0) as? Intent ?: return
        val action = intent.action

        if (BuildConfig.DEBUG) {
            XLog.d("SmsHandlerHook: Received intent action: $action")
            intent.extras?.let { bundle ->
                XLog.d("SmsHandlerHook: Extra keys = %s", bundle.keySet().joinToString(","))
            }
        }

        if (!SmsIntentHookSupport.isSmsAction(action)) {
            return
        }
        val eventId = ensureEventId(intent)
        val runtime = runtimeSession.currentOrResolveWithFallback(param.thisObject)
        runtime?.phoneContext?.let { phoneContext ->
            HookTargetDiagnostics.logInboundSmsRuntimeHitAtInfo(
                source = "SmsHandlerHook#dispatchIntent",
                packageName = phoneContext.packageName,
                processName = phoneContext.applicationInfo?.processName ?: phoneContext.packageName,
                detail = "event_id=$eventId action=$action",
            )
        }
        if (SmsIntentHookSupport.markDispatchHandled(intent, action, DISPATCH_HANDLER_KEY)) {
            XLog.w(
                "SmsHandlerHook duplicate sms suppressed: event_id=%s action=%s source=intent_extra",
                eventId,
                action,
            )
            emitHandler(
                result = "skip",
                reason = "intent_extra_dedupe",
                stage = "dedupe_intent",
                eventIdPresent = true,
            )
            return
        }
        val pluginContext = runtimeSession.currentOrResolve()?.pluginContext
        if (pluginContext != null && shouldSkipDispatchBySharedDedup(pluginContext, eventId, action)) {
            emitHandler(
                result = "skip",
                reason = "shared_store_dedupe",
                stage = "dedupe_shared",
                eventIdPresent = true,
            )
            return
        }
        val pduCount = SmsIntentHookSupport.getPduCount(intent) {
            XLog.w("Diag getPduCount failed: %s", it.message ?: "unknown")
        }
        XLog.w(
            "Diag SMS intent intercepted: event_id=%s action=%s, pduCount=%d, extras=%s",
            eventId,
            action,
            pduCount,
            intent.extras != null,
        )
        val outcome = dispatchIntentHandler.handle(
            intent = intent,
            eventId = eventId,
            inboundSmsHandler = param.thisObject,
            receiver = param.args.getOrNull(receiverIndex),
            hookArgs = param.args,
        )
        if (outcome.inboundBlocked) {
            param.result = null
        }
        emitHandler(
            result = if (outcome.shouldStopDispatch || outcome.inboundBlocked) "ok" else "ok",
            reason = when {
                outcome.inboundBlocked -> "inbound_blocked"
                outcome.shouldStopDispatch -> "stop_dispatch"
                else -> "handled"
            },
            stage = "sms_handler",
            eventIdPresent = true,
        )
        if (outcome.shouldStopDispatch) {
            return
        }
    }

    private fun scheduleBlacklistDelete(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) {
        smsOperationExecutor.execute {
            XLog.withRoute(LogRoute.SMS_HOOK) {
                runCatching {
                    OperateSmsAction(
                        pluginContext,
                        phoneContext,
                        smsMsg,
                        OperateSmsAction.OP_DELETE,
                    ).call()
                }.onFailure {
                    XLog.w("Diag sms blacklist delete task failed: %s", it.message ?: "unknown")
                }
            }
        }
    }

    private fun ensureEventId(intent: Intent): String {
        return XpDispatchCoordinator.ensureIncomingEventId(intent)
    }

    @Suppress("ReturnCount")
    private fun maybeBlockFromDispatchChain(
        methodName: String,
        param: MethodHookParam,
        smsIntent: Intent?,
    ) {
        val intent = smsIntent ?: return
        val action = intent.action
        if (!SmsIntentHookSupport.isSmsAction(action)) return
        val runtime = runtimeSession.currentOrResolveWithFallback(param.thisObject) ?: return
        val pluginContext = runtime.pluginContext
        val phoneContext = runtime.phoneContext
        if (ModuleConflictArbiter.shouldSuppressByRelay(phoneContext, "SmsHandlerHook#$methodName")) {
            logSuppressedOnce("dispatchChain:$methodName")
            return
        }
        val eventId = SmsIntentHookSupport.ensureEventId(intent)
        val evaluation = SmsBlockEvaluator.evaluate(pluginContext, intent, eventId, "dispatch_chain") ?: return
        SmsBlacklistHitRecorder.record(
            pluginContext = pluginContext,
            smsMsg = evaluation.smsMsg,
            blacklistResult = evaluation.blacklistResult,
            decision = evaluation.decision,
            eventId = eventId,
            source = "dispatch_chain",
        )
        if (evaluation.blacklistDeleteOnly && evaluation.smsMsg != null) {
            scheduleBlacklistDelete(pluginContext, phoneContext, evaluation.smsMsg)
        }
        val reason = evaluation.blockReason ?: return
        if (shouldSkipDispatchChainBlock(evaluation.smsMsg, action, reason)) {
            return
        }
        runtimeSession.recordHeartbeat("sms_handler_dispatch_chain")
        XLog.w(
            "Diag SMS dispatch chain block: method=%s reason=%s event_id=%s",
            methodName,
            reason,
            eventId,
        )
        // CodeWorker.parse() is async: only does toast/notification/clipboard side effects.
        // The block decision was already made above. Don't block the dispatch chain.
        smsOperationExecutor.execute {
            CodeWorker(pluginContext, phoneContext, intent, eventId).parse()
        }
        val inbound = param.thisObject ?: return
        val smsReceiver = SmsIntentHookSupport.findRawTableReceiver(param.args)
        if (smsReceiver != null) {
            val blocked = inboundSmsBlocker.blockInboundSms(
                inboundSmsHandler = inbound,
                smsReceiver = smsReceiver,
                reason = reason,
                eventId = eventId,
            )
            if (!blocked) {
                XLog.w(
                    "Diag dispatch chain block aborted: cleanup incomplete method=%s event_id=%s",
                    methodName,
                    eventId,
                )
                return
            }
            if (reason == SmsBlockEvaluator.BLOCK_REASON_PREF_BLOCK) {
                evaluation.smsMsg?.let { smsMsg ->
                    parsedCodeSmsForwarder.forwardIfCodeSms(
                        pluginContext = pluginContext,
                        phoneContext = phoneContext,
                        smsMsg = smsMsg,
                        sourceIntent = intent,
                        eventId = eventId,
                    )
                }
            }
        } else {
            XLog.w(
                "Diag dispatch chain block fallback: receiver unavailable method=%s event_id=%s",
                methodName,
                eventId,
            )
            return
        }
        param.result = SmsIntentHookSupport.defaultResultForType((param.method as? Method)?.returnType)
    }

    private fun shouldSkipDispatchChainBlock(
        smsMsg: SmsMsg?,
        action: String?,
        reason: String,
    ): Boolean {
        val result = dispatchChainBlockDeduplicator.shouldSkip(
            smsMsg = smsMsg,
            action = action,
            reason = reason,
        )
        if (result.shouldSkip) {
            XLog.w(
                "Diag dispatch chain block duplicate skip: action=%s reason=%s ageMs=%d",
                action,
                reason,
                result.ageMs ?: 0L,
            )
            return true
        }
        return false
    }

    private fun shouldSkipDispatchBySharedDedup(
        pluginContext: Context,
        eventId: String,
        action: String?,
    ): Boolean {
        val result = dispatchIntentDeduplicator.shouldSkipInMemory(eventId, action)
        if (result.shouldSkip) {
            XLog.d(
                "Diag SMS dispatch duplicate skip (memory): event_id=%s action=%s ageMs=%d",
                eventId,
                action,
                result.ageMs ?: 0L,
            )
            return true
        }

        val key = result.key ?: return false
        val timestampMs = result.timestampMs ?: return false
        smsOperationExecutor.execute {
            syncSharedDedupToFile(pluginContext, key, timestampMs)
        }

        return false
    }

    private fun syncSharedDedupToFile(pluginContext: Context, key: String, timestampMs: Long) {
        runCatching {
            val file = File(
                pluginContext.getExternalFilesDir(null) ?: pluginContext.filesDir,
                SmsDispatchIntentDeduplicator.DEFAULT_FILE_NAME,
            )
            dispatchIntentDeduplicator.syncFileIfLockAvailable(file, key, timestampMs)
        }.onFailure {
            XLog.d(
                "Diag SMS dispatch dedup background sync failed: %s",
                it.message ?: it.javaClass.simpleName,
            )
        }
    }

    private fun logSuppressedOnce(stage: String) {
        if (suppressionLogged) return
        synchronized(this) {
            if (suppressionLogged) return
            XLog.w(
                "SmsHandlerHook suppressed: reason=%s stage=%s package=%s",
                ModuleConflictArbiter.SUPPRESSION_REASON,
                stage,
                ModuleConflictArbiter.TARGET_RELAY_PACKAGE,
            )
            suppressionLogged = true
        }
    }

    override fun onHotReloading() {
        smsInboxObserver?.unregister()
        smsInboxObserver = null
        smsOperationExecutor.shutdownNow()
    }


    private fun emitHandler(
        result: String,
        reason: String,
        stage: String,
        eventIdPresent: Boolean = false,
        statusOk: Boolean = true,
        errorClass: String? = null,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to "0",
            "process" to "hook",
            "stage" to stage,
            "reason" to reason,
            "source" to "sms_handler",
            "msg_type" to "sms",
        )
        if (eventIdPresent) {
            attrs["event_id_present"] = "true"
        }
        if (!errorClass.isNullOrBlank()) {
            attrs["error_class"] = errorClass
        }
        MagiskOtel.event(name = "sms.process", attributes = attrs, statusOk = statusOk)
    }

    companion object {
        private const val TELEPHONY_PACKAGE = "com.android.internal.telephony"
        private const val SMS_HANDLER_CLASS = "$TELEPHONY_PACKAGE.InboundSmsHandler"
        private const val DISPATCH_HANDLER_KEY = "sms_handler"
        private val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private val dispatchIntentDeduplicator = SmsDispatchIntentDeduplicator()
        private val dispatchChainBlockDeduplicator = SmsDispatchChainBlockDeduplicator()
    }
}
