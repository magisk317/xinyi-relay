package io.github.magisk317.relay.xp.hook.code

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.xp.HookTargetDiagnostics
import io.github.magisk317.smscode.xposed.utils.ModuleActivationStore
import io.github.magisk317.relay.xpbridge.SmsMsg
import io.github.magisk317.relay.xpbridge.XpDispatchCoordinator
import io.github.magisk317.smscode.verification.SmsIntentHookSupport
import io.github.magisk317.smscode.xposed.hook.telephony.InboundSmsBlocker
import io.github.magisk317.relay.xp.hook.SmsHookRuntimeContext
import io.github.magisk317.relay.xp.hook.SmsHookRuntimeSession
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xp.helper.SmsCodeConflictNoticeHelper
import io.github.magisk317.smscode.xposed.helper.XposedWrapper
import io.github.magisk317.smscode.xposed.hook.BaseHook
import io.github.magisk317.relay.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.smscode.xposed.hookapi.HookEnv
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.HookBridge
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam
import java.io.File
import java.io.RandomAccessFile
import java.lang.reflect.Method
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.Executors
/**
 * Hook class com.android.internal.telephony.InboundSmsHandler
 */
class SmsHandlerHook : BaseHook() {
    private val runtimeSession = SmsHookRuntimeSession(SMSCODE_PACKAGE, ANDROID_PHONE_PACKAGE)
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

    override fun onLoadPackage(lpparam: LoadParam) {
        if (ANDROID_PHONE_PACKAGE == lpparam.packageName) {
            HookTargetDiagnostics.logTargetProcessHitIfVerbose(
                hookName = "SmsHandlerHook",
                loadParam = lpparam,
                targetPackage = ANDROID_PHONE_PACKAGE,
            )
            XLog.i("SmsCode initializing")
            printDeviceInfo()
            val classLoader = lpparam.classLoader ?: run {
                XLog.w("SmsHandlerHook skipped: classLoader is null for %s", lpparam.packageName)
                return
            }
            try {
                hookSmsHandler(classLoader)
            } catch (e: Throwable) {
                XLog.e("Failed to hook SmsHandler", e)
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
        return HookEnv.api.getXposedBridgeVersion() ?: HookEnv.api.getApiVersion()
    }

    private fun hookSmsHandler(classloader: ClassLoader) {
        hookConstructor(classloader)
        hookDispatchIntent(classloader)
        hookSmsDispatcherChain(classloader)
    }

    private fun hookConstructor(classloader: ClassLoader) {
        // minSdkVersion 35: Only hook for Android 14+ / 15+
        hookConstructor34(classloader)
    }

    // Android 14+
    private fun hookConstructor34(classLoader: ClassLoader) {
        XLog.i("Hooking InboundSmsHandler constructor for android v34+")
        val smsHandlerClazz = XposedWrapper.findClass(SMS_HANDLER_CLASS, classLoader)
        if (smsHandlerClazz != null) {
            HookBridge.hookAllConstructors(smsHandlerClazz, ConstructorHook())
        } else {
            HookTargetDiagnostics.logTargetMissIfVerbose(
                hookName = "SmsHandlerHook",
                loadParam = LoadParam(ANDROID_PHONE_PACKAGE, ANDROID_PHONE_PACKAGE, classLoader),
                reason = "class_not_found",
                detail = SMS_HANDLER_CLASS,
            )
        }
    }

    private fun hookDispatchIntent(classloader: ClassLoader) {
        // minSdkVersion 35: Only hook for Android 10+ / 15+
        hookDispatchIntent29(classloader)
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
        val clazz = XposedWrapper.findClass(className, classLoader) ?: return
        methodNames.forEach { name ->
            val methods = clazz.declaredMethods.filter { it.name == name }
            if (methods.isEmpty()) return@forEach
            methods.forEach { method ->
                XposedWrapper.hookMethod(
                    method,
                    object : MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            maybeBlockFromDispatchChain(
                                methodName = name,
                                param = param,
                                smsIntent = extractOrBuildSmsIntent(
                                    param.args,
                                    Telephony.Sms.Intents.SMS_DELIVER_ACTION,
                                ),
                            )
                            val action = extractIntentAction(param.args)
                            XLog.w(
                                "Diag SMS dispatch chain: class=%s method=%s action=%s args=%d",
                                className,
                                name,
                                action ?: "<none>",
                                param.args.size,
                            )
                        }
                    },
                )
            }
        }
    }

    private fun extractIntentAction(args: Array<Any?>?): String? {
        if (args == null) return null
        for (arg in args) {
            if (arg is Intent) {
                return arg.action
            }
        }
        return null
    }

    // Android 10+
    private fun hookDispatchIntent29(classLoader: ClassLoader) {
        XLog.d("Hooking dispatchIntent() for Android v29+")
        val inboundSmsHandlerClass = XposedWrapper.findClass(SMS_HANDLER_CLASS, classLoader) ?: run {
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
            XposedWrapper.hookMethod(it, DispatchIntentHook(receiverIndex))
        } ?: run {
            XLog.e("Method %s for Class %s cannot found", dispatchIntentMethodName, SMS_HANDLER_CLASS)
            HookTargetDiagnostics.logTargetMissIfVerbose(
                hookName = "SmsHandlerHook",
                loadParam = LoadParam(ANDROID_PHONE_PACKAGE, ANDROID_PHONE_PACKAGE, classLoader),
                reason = "method_not_found",
                detail = "$SMS_HANDLER_CLASS#$dispatchIntentMethodName",
            )
        }
    }

    private inner class ConstructorHook : MethodHook() {
        @Throws(Throwable::class)
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                afterConstructorHandler(param)
            } catch (e: Throwable) {
                XLog.e("Error occurred in constructor hook", e)
                throw e
            }
        }
    }

    private fun afterConstructorHandler(param: MethodHookParam) {
        val context = param.args.getOrNull(1) as? Context ?: return
        constructorInitializer.handle(context)
    }

    private fun registerSmsInboxObserver(runtime: SmsHookRuntimeContext) {
        if (smsInboxObserver != null) return
        smsInboxObserver = SmsInboxObserver(runtime.pluginContext, runtime.phoneContext).also { it.register() }
    }

    private inner class DispatchIntentHook(private val mReceiverIndex: Int) : MethodHook() {
        @Throws(Throwable::class)
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                beforeDispatchIntentHandler(param, mReceiverIndex)
            } catch (e: Throwable) {
                XLog.e("Error occurred in dispatchIntent() hook, ", e)
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
        if (SmsIntentHookSupport.markDispatchHandled(intent, action, DISPATCH_HANDLER_KEY)) {
            XLog.w(
                "SmsHandlerHook duplicate sms suppressed: event_id=%s action=%s source=intent_extra",
                eventId,
                action,
            )
            return
        }
        val pluginContext = runtimeSession.currentOrResolve()?.pluginContext
        if (pluginContext != null && shouldSkipDispatchBySharedDedup(pluginContext, eventId, action)) {
            return
        }
        val pduCount = getPduCount(intent)
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
        if (outcome.shouldStopDispatch) {
            return
        }
    }

    private fun getPduCount(intent: Intent): Int {
        return try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)?.size ?: -1
        } catch (t: Throwable) {
            XLog.w("Diag getPduCount failed: %s", t.message ?: "unknown")
            -1
        }
    }

    private fun scheduleBlacklistDelete(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) {
        SMS_OPERATION_EXECUTOR.execute {
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
        val runtime = runtimeSession.currentOrResolve() ?: return
        val pluginContext = runtime.pluginContext
        val phoneContext = runtime.phoneContext
        if (ModuleConflictArbiter.shouldSuppressByRelay(phoneContext, "SmsHandlerHook#$methodName")) {
            logSuppressedOnce("dispatchChain:$methodName")
            return
        }
        val eventId = SmsIntentHookSupport.ensureEventId(intent)
        val evaluation = SmsBlockEvaluator.evaluate(pluginContext, intent, eventId, "dispatch_chain") ?: return
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
        CodeWorker(pluginContext, phoneContext, intent, eventId).parse()
        val inbound = param.thisObject ?: return
        val smsReceiver = findRawTableReceiver(param.args)
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
        param.result = defaultResultForType((param.method as? Method)?.returnType)
    }

    private fun extractOrBuildSmsIntent(args: Array<Any?>?, fallbackAction: String): Intent? {
        if (args == null) return null
        args.forEach { arg ->
            if (arg is Intent) {
                return arg
            }
        }
        val pduList = args.firstNotNullOfOrNull { arg ->
            val array = arg as? Array<*> ?: return@firstNotNullOfOrNull null
            val pdus = array.mapNotNull { it as? ByteArray }
            if (pdus.isEmpty() || pdus.size != array.size) null else pdus
        } ?: return null
        val format = args.firstNotNullOfOrNull { arg ->
            val text = arg as? String ?: return@firstNotNullOfOrNull null
            if (text.equals("3gpp", ignoreCase = true) || text.equals("3gpp2", ignoreCase = true)) {
                text
            } else {
                null
            }
        }
        return Intent(fallbackAction).apply {
            putExtra("pdus", pduList.toTypedArray())
            if (!format.isNullOrBlank()) {
                putExtra("format", format)
            }
        }
    }

    private fun findRawTableReceiver(args: Array<Any?>?): Any? {
        if (args == null) return null
        return args.firstOrNull { candidate ->
            candidate != null &&
                hasField(candidate, "mDeleteWhere") &&
                hasField(candidate, "mDeleteWhereArgs")
        }
    }

    private fun hasField(instance: Any, fieldName: String): Boolean {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            if (current.declaredFields.any { it.name == fieldName }) {
                return true
            }
            current = current.superclass
        }
        return false
    }

    private fun shouldSkipDispatchChainBlock(
        smsMsg: SmsMsg?,
        action: String?,
        reason: String,
    ): Boolean {
        if (smsMsg == null || action.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val key = buildString {
            append(senderHash(smsMsg.sender))
            append('|')
            append(smsMsg.body.orEmpty().hashCode())
            append('|')
            append(smsMsg.date)
            append('|')
            append(action)
            append('|')
            append(reason)
        }
        synchronized(DISPATCH_CHAIN_BLOCK_LOCK) {
            val iterator = dispatchChainBlockHistory.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > DISPATCH_CHAIN_BLOCK_WINDOW_MS) {
                    iterator.remove()
                }
            }
            val last = dispatchChainBlockHistory[key]
            if (last != null && now - last <= DISPATCH_CHAIN_BLOCK_WINDOW_MS) {
                XLog.w(
                    "Diag dispatch chain block duplicate skip: action=%s reason=%s ageMs=%d",
                    action,
                    reason,
                    now - last,
                )
                return true
            }
            dispatchChainBlockHistory[key] = now
            return false
        }
    }

    private fun senderHash(sender: String?): String {
        val value = sender.orEmpty()
        if (value.isBlank()) return "none"
        return Integer.toHexString(value.hashCode())
    }

    private fun defaultResultForType(type: Class<*>?): Any? {
        return when (type) {
            null, Void.TYPE, Void::class.java -> null
            Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> false
            Int::class.javaPrimitiveType, Int::class.javaObjectType -> 0
            Long::class.javaPrimitiveType, Long::class.javaObjectType -> 0L
            Float::class.javaPrimitiveType, Float::class.javaObjectType -> 0f
            Double::class.javaPrimitiveType, Double::class.javaObjectType -> 0.0
            Short::class.javaPrimitiveType, Short::class.javaObjectType -> 0.toShort()
            Byte::class.javaPrimitiveType, Byte::class.javaObjectType -> 0.toByte()
            Char::class.javaPrimitiveType, Char::class.javaObjectType -> 0.toChar()
            else -> null
        }
    }

    private fun shouldSkipDispatchBySharedDedup(
        pluginContext: Context,
        eventId: String,
        action: String?,
    ): Boolean {
        if (eventId.isBlank() || action.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val key = "$eventId|$action"
        return runCatching {
            val file = File(pluginContext.getExternalFilesDir(null) ?: pluginContext.filesDir, DISPATCH_DEDUP_FILE_NAME)
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").use { raf ->
                raf.channel.use { channel ->
                    channel.lock().use {
                        val entries = readDispatchDedupEntries(raf)
                        val iterator = entries.entries.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (now - entry.value > DISPATCH_DEDUP_WINDOW_MS) {
                                iterator.remove()
                            }
                        }
                        val last = entries[key]
                        if (last != null && now - last <= DISPATCH_DEDUP_WINDOW_MS) {
                            XLog.w(
                                "Diag SMS dispatch duplicate skip: event_id=%s action=%s source=shared_store ageMs=%d",
                                eventId,
                                action,
                                now - last,
                            )
                            writeDispatchDedupEntries(raf, entries)
                            return true
                        }
                        entries[key] = now
                        while (entries.size > MAX_DISPATCH_DEDUP_ENTRIES) {
                            val firstKey = entries.entries.firstOrNull()?.key ?: break
                            entries.remove(firstKey)
                        }
                        writeDispatchDedupEntries(raf, entries)
                        false
                    }
                }
            }
        }.onFailure {
            XLog.w(
                "Diag SMS dispatch shared dedup failed: event_id=%s action=%s err=%s",
                eventId,
                action,
                it.message ?: it.javaClass.simpleName,
            )
        }.getOrDefault(false)
    }

    private fun readDispatchDedupEntries(raf: RandomAccessFile): LinkedHashMap<String, Long> {
        val entries = LinkedHashMap<String, Long>()
        raf.seek(0L)
        while (true) {
            val rawLine = raf.readLine() ?: break
            val line = rawLine.trim()
            if (line.isBlank()) continue
            val split = line.indexOf('=')
            if (split <= 0) continue
            val key = line.substring(0, split)
            val value = line.substring(split + 1).toLongOrNull() ?: continue
            entries[key] = value
        }
        return entries
    }

    private fun writeDispatchDedupEntries(
        raf: RandomAccessFile,
        entries: LinkedHashMap<String, Long>,
    ) {
        raf.setLength(0L)
        raf.seek(0L)
        val content = buildString {
            entries.forEach { (key, value) ->
                append(key)
                append('=')
                append(value)
                append('\n')
            }
        }
        raf.write(content.toByteArray())
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

    companion object {
        const val ANDROID_PHONE_PACKAGE = "com.android.phone"
        private const val TELEPHONY_PACKAGE = "com.android.internal.telephony"
        private const val SMS_HANDLER_CLASS = "$TELEPHONY_PACKAGE.InboundSmsHandler"
        private const val DISPATCH_HANDLER_KEY = "sms_handler"
        private const val DISPATCH_DEDUP_FILE_NAME = "dispatch_dedup"
        private const val DISPATCH_DEDUP_WINDOW_MS = 8_000L
        private const val MAX_DISPATCH_DEDUP_ENTRIES = 256
        private const val DISPATCH_CHAIN_BLOCK_WINDOW_MS = 8_000L
        private val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private val SMS_OPERATION_EXECUTOR = Executors.newSingleThreadExecutor()
        private val dispatchChainBlockHistory = LinkedHashMap<String, Long>()
        private val DISPATCH_CHAIN_BLOCK_LOCK = Any()
    }
}
