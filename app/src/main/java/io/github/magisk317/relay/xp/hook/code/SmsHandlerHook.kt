package io.github.magisk317.relay.xp.hook.code

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Message
import android.provider.Telephony
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.common.utils.ActivationDiagnosticsStore
import io.github.magisk317.relay.common.constant.NotificationConst
import io.github.magisk317.smscode.core.utils.ModuleActivationStore
import io.github.magisk317.relay.common.utils.NotificationUtils
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.common.utils.RuntimeLogStore
import io.github.magisk317.relay.common.utils.SmsBlacklistUtils
import io.github.magisk317.smscode.core.utils.XLog
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.platform.ipc.ForwardPayloadFactory
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xp.helper.SmsCodeConflictNoticeHelper
import io.github.magisk317.smscode.core.helper.XposedWrapper
import io.github.magisk317.smscode.core.hook.BaseHook
import io.github.magisk317.relay.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.smscode.core.hookapi.HookEnv
import io.github.magisk317.smscode.core.hookapi.MethodHook
import io.github.magisk317.smscode.core.hookapi.HookBridge
import io.github.magisk317.smscode.core.hookapi.HookHelpers
import io.github.magisk317.smscode.core.hookapi.LoadParam
import io.github.magisk317.smscode.core.hookapi.MethodHookParam
import java.lang.reflect.Method
import java.util.concurrent.Executors
/**
 * Hook class com.android.internal.telephony.InboundSmsHandler
 */
class SmsHandlerHook : BaseHook() {

    private var mPhoneContext: Context? = null
    private var mPluginContext: Context? = null
    private var smsInboxObserver: SmsInboxObserver? = null
    @Volatile
    private var suppressionLogged = false

    override fun onLoadPackage(lpparam: LoadParam) {
        if (ANDROID_PHONE_PACKAGE == lpparam.packageName) {
            XLog.i("SmsCode initializing")
            printDeviceInfo()
            try {
                hookSmsHandler(lpparam.classLoader)
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
        if (mPhoneContext == null) {
            mPhoneContext = context
            try {
                mPluginContext = mPhoneContext?.createPackageContext(
                    SMSCODE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
                if (mPluginContext != null) {
                    val pluginContext = mPluginContext ?: return
                    SmsCodeConflictNoticeHelper.initNotificationChannel(pluginContext, context)
                    val suppressByRelay = ModuleConflictArbiter.shouldSuppressByRelay(
                        mPhoneContext,
                        "SmsHandlerHook#constructor",
                    )
                    if (PrefsReader.showCodeNotification(pluginContext)) {
                        initNotificationChannel()
                        if (!suppressByRelay) {
                            registerCopyCodeReceiver()
                        }
                    }
                    ModuleActivationStore.markActivated(pluginContext)
                    ActivationDiagnosticsStore.recordHookHeartbeat(
                        context = pluginContext,
                        packageName = ANDROID_PHONE_PACKAGE,
                        processName = context.applicationInfo?.processName ?: ANDROID_PHONE_PACKAGE,
                        source = "sms_handler_constructor",
                        verboseLogging = PrefsReader.isVerboseLogMode(pluginContext),
                        route = RuntimeLogStore.ROUTE_SMS_HOOK,
                    )
                    if (suppressByRelay) {
                        logSuppressedOnce("constructor")
                    } else {
                        registerSmsInboxObserver()
                    }
                } else {
                    XLog.e("Plugin context is null after creation attempt")
                }
            } catch (e: Exception) {
                XLog.e("Create plugin context failed: %s", e)
            }
        }
    }

    private fun initNotificationChannel() {
        val channelId = NotificationConst.CHANNEL_ID_RELAY_NOTIFICATION
        val channelName = getPluginContext()?.getString(R.string.channel_name_relay_notification) ?: ""
        mPhoneContext?.let {
            NotificationUtils.createNotificationChannel(
                it,
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH,
            )
            XLog.d("Init notification channel succeed")
        }
    }

    private fun registerCopyCodeReceiver() {
        val pluginContext = mPluginContext ?: return
        if (!PrefsReader.showCodeNotification(pluginContext)) return
        mPhoneContext?.let {
            CopyCodeReceiver.registerMe(it)
            XLog.d("Register copy code receiver")
        }
    }

    private fun registerSmsInboxObserver() {
        val pluginContext = mPluginContext ?: return
        val phoneContext = mPhoneContext ?: return
        if (smsInboxObserver != null) return
        smsInboxObserver = SmsInboxObserver(pluginContext, phoneContext).also { it.register() }
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

        if (action != Telephony.Sms.Intents.SMS_DELIVER_ACTION &&
            action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        ) {
            return
        }
        val eventId = ensureEventId(intent)
        val pduCount = getPduCount(intent)
        XLog.w(
            "Diag SMS intent intercepted: event_id=%s action=%s, pduCount=%d, extras=%s",
            eventId,
            action,
            pduCount,
            intent.extras != null,
        )

        val pluginContext = getPluginContext()
        val phoneContext = mPhoneContext
        if (pluginContext == null || phoneContext == null) {
            XLog.e("Context is null, skip parsing. pluginContext: %s, phoneContext: %s", pluginContext, phoneContext)
            return
        }
        ActivationDiagnosticsStore.recordHookHeartbeat(
            context = pluginContext,
            packageName = ANDROID_PHONE_PACKAGE,
            processName = phoneContext.applicationInfo?.processName ?: ANDROID_PHONE_PACKAGE,
            source = "sms_handler_dispatch",
            verboseLogging = PrefsReader.isVerboseLogMode(pluginContext),
            route = RuntimeLogStore.ROUTE_SMS_HOOK,
        )
        if (ModuleConflictArbiter.shouldSuppressByRelay(phoneContext, "SmsHandlerHook#dispatchIntent")) {
            logSuppressedOnce("dispatchIntent")
            SmsCodeConflictNoticeHelper.notifyConflictOnSms(
                pluginContext,
                phoneContext,
                eventId,
                "SmsHandlerHook#dispatchIntent",
            )
            return
        }
        val smsMsg = runCatching { SmsMsg.fromIntent(intent) }.getOrNull()
        val blacklistResult = SmsBlacklistUtils.match(pluginContext, smsMsg?.sender, smsMsg?.body)
        if (blacklistResult.matched) {
            XLog.w(
                "Diag sms blacklist matched: event_id=%s type=%s, pattern=%s, delete=%s, block=%s",
                eventId,
                blacklistResult.matchType,
                blacklistResult.pattern,
                blacklistResult.actionDelete,
                blacklistResult.actionBlock,
            )
            if (blacklistResult.actionDelete && !blacklistResult.actionBlock && smsMsg != null) {
                scheduleBlacklistDelete(pluginContext, phoneContext, smsMsg)
            }
            if (blacklistResult.actionBlock) {
                XLog.w("Diag sms block reason=%s event_id=%s", BLOCK_REASON_BLACKLIST, eventId)
                param.args.getOrNull(receiverIndex)?.let { receiver ->
                    val inbound = param.thisObject ?: return
                    deleteRawTableAndSendMessage(
                        inboundSmsHandler = inbound,
                        smsReceiver = receiver,
                        reason = BLOCK_REASON_BLACKLIST,
                        eventId = eventId,
                    )
                    param.result = null
                }
                return
            }
        }

        val parseResult = CodeWorker(pluginContext, phoneContext, intent, eventId).parse()
        if (parseResult == null) {
            XLog.w("Diag parse result is null: event_id=%s no code matched or parse failed", eventId)
        } else {
            XLog.w("Diag parse result: event_id=%s blockSms=%s", eventId, parseResult.isBlockSms)
        }
        if (parseResult != null) {
            if (parseResult.isBlockSms) {
                XLog.w("Diag sms block reason=%s event_id=%s", BLOCK_REASON_PREF_BLOCK, eventId)
                param.args.getOrNull(receiverIndex)?.let { receiver ->
                    val inbound = param.thisObject ?: return
                    deleteRawTableAndSendMessage(
                        inboundSmsHandler = inbound,
                        smsReceiver = receiver,
                        reason = BLOCK_REASON_PREF_BLOCK,
                        eventId = eventId,
                    )
                    param.result = null
                }
            } else {
                XLog.w(
                    "Diag allow system inbox persist: event_id=%s sender_hash=%s body_len=%d",
                    eventId,
                    senderHash(smsMsg?.sender),
                    smsMsg?.body?.length ?: 0,
                )
            }
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
                    OperateSmsAction.FORCE_DELETE,
                ).call()
            }.onFailure {
                XLog.w("Diag sms blacklist delete task failed: %s", it.message ?: "unknown")
            }
        }
    }

    private fun deleteRawTableAndSendMessage(
        inboundSmsHandler: Any,
        smsReceiver: Any,
        reason: String,
        eventId: String,
    ) {
        XLog.w("Diag raw-table delete start: reason=%s event_id=%s", reason, eventId)
        val token = Binder.clearCallingIdentity()
        try {
            deleteFromRawTable(inboundSmsHandler, smsReceiver, reason, eventId)
        } catch (e: Throwable) {
            XLog.e("Error occurs when delete SMS data from raw table", e)
        } finally {
            Binder.restoreCallingIdentity(token)
        }

        try {
            sendEventBroadcastComplete(inboundSmsHandler, reason, eventId)
        } catch (e: Throwable) {
            XLog.e("Error occurs when sending broadcast complete", e)
        }
    }

    private fun sendEventBroadcastComplete(inboundSmsHandler: Any, reason: String, eventId: String) {
        XLog.d("Send event(EVENT_BROADCAST_COMPLETE): reason=%s event_id=%s", reason, eventId)
        if (trySendMessage(inboundSmsHandler, EVENT_BROADCAST_COMPLETE)) {
            return
        }
        if (!loggedSendMessageSignatures) {
            loggedSendMessageSignatures = true
            logMethodSignatures(
                "Diag sendMessage signatures",
                inboundSmsHandler.javaClass,
                "sendMessage",
            )
        }
    }

    @Throws(ReflectiveOperationException::class)
    private fun deleteFromRawTable(inboundSmsHandler: Any, smsReceiver: Any, reason: String, eventId: String) {
        // minSdkVersion 35: Always use Android 24+ method
        deleteFromRawTable24(inboundSmsHandler, smsReceiver, reason, eventId)
    }

    @Throws(ReflectiveOperationException::class)
    private fun deleteFromRawTable24(inboundSmsHandler: Any, smsReceiver: Any, reason: String, eventId: String) {
        XLog.d("Delete raw SMS data from database on Android 24+: reason=%s event_id=%s", reason, eventId)
        val deleteWhere = HookHelpers.getObjectField(smsReceiver, "mDeleteWhere")
        val deleteWhereArgs = HookHelpers.getObjectField(smsReceiver, "mDeleteWhereArgs")
        val markDeleted = 2
        val handlerClass = HookHelpers.findClass(SMS_HANDLER_CLASS, inboundSmsHandler.javaClass.classLoader)
        val cached = cachedDeleteRawMethod
        if (cached != null) {
            val args = buildDeleteRawArgs(cached.parameterTypes, deleteWhere, deleteWhereArgs, markDeleted)
            if (args != null) {
                cached.invoke(inboundSmsHandler, *args)
                return
            } else {
                cachedDeleteRawMethod = null
            }
        }

        val methods = collectMethods(handlerClass, "deleteFromRawTable")
        var lastError: Throwable? = null
        for (method in methods) {
            val args = buildDeleteRawArgs(method.parameterTypes, deleteWhere, deleteWhereArgs, markDeleted) ?: continue
            try {
                method.invoke(inboundSmsHandler, *args)
                cachedDeleteRawMethod = method
                return
            } catch (e: Throwable) {
                lastError = e
            }
        }
        if (!loggedDeleteRawSignatures) {
            loggedDeleteRawSignatures = true
            logMethodSignatures(
                "Diag deleteFromRawTable signatures",
                handlerClass,
                "deleteFromRawTable",
            )
        }
        if (lastError != null) {
            throw lastError
        } else {
            throw NoSuchMethodException("No suitable method for ${handlerClass.name}#deleteFromRawTable")
        }
    }

    private fun trySendMessage(inboundSmsHandler: Any, what: Int): Boolean {
        val cached = cachedSendMessageMethod
        if (cached != null) {
            val args = buildSendMessageArgs(cached.parameterTypes, inboundSmsHandler, what)
            if (args != null) {
                return runCatching {
                    cached.invoke(inboundSmsHandler, *args)
                    true
                }.getOrElse {
                    cachedSendMessageMethod = null
                    false
                }
            } else {
                cachedSendMessageMethod = null
            }
        }

        val methods = collectMethods(inboundSmsHandler.javaClass, "sendMessage")
        for (method in methods) {
            val args = buildSendMessageArgs(method.parameterTypes, inboundSmsHandler, what) ?: continue
            val ok = runCatching {
                method.invoke(inboundSmsHandler, *args)
                cachedSendMessageMethod = method
                true
            }.getOrElse { false }
            if (ok) return true
        }
        return false
    }

    private fun buildSendMessageArgs(
        parameterTypes: Array<Class<*>>,
        inboundSmsHandler: Any,
        what: Int,
    ): Array<Any?>? {
        val message = obtainMessage(inboundSmsHandler, what)
        val intValues = ArrayDeque<Any?>(listOf(what, 0))
        val longValues = ArrayDeque<Any?>(listOf(0L))
        val boolValues = ArrayDeque<Any?>(listOf(false))
        val args = arrayOfNulls<Any?>(parameterTypes.size)
        for (i in parameterTypes.indices) {
            val type = parameterTypes[i]
            when {
                type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType ->
                    args[i] = if (intValues.isNotEmpty()) intValues.removeFirst() else 0
                type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType ->
                    args[i] = if (longValues.isNotEmpty()) longValues.removeFirst() else 0L
                type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType ->
                    args[i] = if (boolValues.isNotEmpty()) boolValues.removeFirst() else false
                type == Message::class.java -> args[i] = message
                else -> args[i] = null
            }
        }
        return args
    }

    private fun obtainMessage(inboundSmsHandler: Any, what: Int): Message {
        return runCatching {
            HookHelpers.callMethod(inboundSmsHandler, "obtainMessage", what) as? Message
        }.getOrNull() ?: Message.obtain().apply { this.what = what }
    }

    private fun buildDeleteRawArgs(
        parameterTypes: Array<Class<*>>,
        deleteWhere: Any?,
        deleteWhereArgs: Any?,
        markDeleted: Int,
    ): Array<Any?>? {
        val stringValues = ArrayDeque<Any?>(listOf(deleteWhere, PERSISTENT_DEVICE_ID_DEFAULT, null))
        val intValues = ArrayDeque<Any?>(listOf(markDeleted, 0))
        val longValues = ArrayDeque<Any?>(listOf(0L))
        val boolValues = ArrayDeque<Any?>(listOf(false))
        val args = arrayOfNulls<Any?>(parameterTypes.size)
        for (i in parameterTypes.indices) {
            val type = parameterTypes[i]
            when {
                type == String::class.java -> args[i] = if (stringValues.isNotEmpty()) stringValues.removeFirst() else null
                type.isArray && type.componentType == String::class.java ->
                    args[i] = deleteWhereArgs
                type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType ->
                    args[i] = if (intValues.isNotEmpty()) intValues.removeFirst() else 0
                type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType ->
                    args[i] = if (longValues.isNotEmpty()) longValues.removeFirst() else 0L
                type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType ->
                    args[i] = if (boolValues.isNotEmpty()) boolValues.removeFirst() else false
                else -> args[i] = null
            }
        }
        return args
    }

    private fun collectMethods(clazz: Class<*>, methodName: String): List<Method> {
        val methods = mutableListOf<Method>()
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods
                .filter { it.name == methodName }
                .forEach { method ->
                    method.isAccessible = true
                    methods += method
                }
            current = current.superclass
        }
        return methods
    }

    private fun logMethodSignatures(tag: String, clazz: Class<*>, methodName: String) {
        val methods = collectMethods(clazz, methodName)
        if (methods.isEmpty()) {
            XLog.w("%s: no method %s in %s", tag, methodName, clazz.name)
            return
        }
        val signatures = methods.joinToString(limit = 80, truncated = "...") { method ->
            val params = method.parameterTypes.joinToString(",") { it.name }
            "${method.name}($params):${method.returnType.name}"
        }
        XLog.w("%s: %s methods=[%s]", tag, clazz.name, signatures)
    }

    private fun ensureEventId(intent: Intent): String {
        return ForwardPayloadFactory.ensureSmsEventId(intent)
    }

    private fun senderHash(sender: String?): String {
        val value = sender.orEmpty()
        if (value.isBlank()) return "none"
        return Integer.toHexString(value.hashCode())
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

    private fun getPluginContext(): Context? {
        if (mPluginContext == null) {
            try {
                mPluginContext = mPhoneContext?.createPackageContext(
                    SMSCODE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
            } catch (e: Exception) {
                XLog.e("Create plugin context failed: %s", e)
            }
        }
        return mPluginContext
    }

    companion object {
        const val ANDROID_PHONE_PACKAGE = "com.android.phone"
        private const val TELEPHONY_PACKAGE = "com.android.internal.telephony"
        private const val SMS_HANDLER_CLASS = "$TELEPHONY_PACKAGE.InboundSmsHandler"
        private val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private const val EVENT_BROADCAST_COMPLETE = 3
        private const val BLOCK_REASON_BLACKLIST = "blacklist_block"
        private const val BLOCK_REASON_PREF_BLOCK = "pref_block_sms"
        private const val PERSISTENT_DEVICE_ID_DEFAULT = "default:0"
        private val SMS_OPERATION_EXECUTOR = Executors.newSingleThreadExecutor()
        @Volatile
        private var cachedDeleteRawMethod: Method? = null
        @Volatile
        private var cachedSendMessageMethod: Method? = null
        @Volatile
        private var loggedDeleteRawSignatures = false
        @Volatile
        private var loggedSendMessageSignatures = false

    }
}
