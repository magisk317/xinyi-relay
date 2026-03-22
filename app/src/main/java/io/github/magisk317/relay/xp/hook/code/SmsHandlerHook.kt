package io.github.magisk317.relay.xp.hook.code

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.common.constant.NotificationConst
import io.github.magisk317.relay.common.utils.NotificationUtils
import io.github.magisk317.smscode.core.utils.ModuleActivationStore
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.xp.XpDispatchCoordinator
import io.github.magisk317.relay.xp.XpPrefs
import io.github.magisk317.relay.xp.hook.SmsHookRuntimeContext
import io.github.magisk317.relay.xp.hook.SmsHookRuntimeSession
import io.github.magisk317.smscode.core.utils.XLog
import io.github.magisk317.relay.xp.helper.ModuleConflictArbiter
import io.github.magisk317.relay.xp.helper.SmsCodeConflictNoticeHelper
import io.github.magisk317.smscode.core.helper.XposedWrapper
import io.github.magisk317.smscode.core.hook.BaseHook
import io.github.magisk317.relay.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.smscode.core.hookapi.HookEnv
import io.github.magisk317.smscode.core.hookapi.MethodHook
import io.github.magisk317.smscode.core.hookapi.HookBridge
import io.github.magisk317.smscode.core.hookapi.LoadParam
import io.github.magisk317.smscode.core.hookapi.MethodHookParam
import java.lang.reflect.Method
import java.util.concurrent.Executors
/**
 * Hook class com.android.internal.telephony.InboundSmsHandler
 */
class SmsHandlerHook : BaseHook() {
    private val runtimeSession = SmsHookRuntimeSession(SMSCODE_PACKAGE, ANDROID_PHONE_PACKAGE)
    private val inboundSmsBlocker = InboundSmsBlocker(SMS_HANDLER_CLASS)
    private val constructorInitializer = SmsHookConstructorInitializer(
        runtimeInitializer = runtimeSession::initialize,
        notificationChannelInitializer = ::initNotificationChannel,
        copyCodeRegistrar = ::registerCopyCodeReceiver,
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
        constructorInitializer.handle(context)
    }

    private fun initNotificationChannel(runtime: SmsHookRuntimeContext) {
        val channelId = NotificationConst.CHANNEL_ID_RELAY_NOTIFICATION
        val channelName = runtime.pluginContext.getString(R.string.channel_name_relay_notification)
        NotificationUtils.createNotificationChannel(
            runtime.phoneContext,
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH,
        )
        XLog.d("Init notification channel succeed")
    }

    private fun registerCopyCodeReceiver(runtime: SmsHookRuntimeContext) {
        if (!XpPrefs.showCodeNotification(runtime.pluginContext)) return
        CopyCodeReceiver.registerMe(runtime.phoneContext)
        XLog.d("Register copy code receiver")
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
        val outcome = dispatchIntentHandler.handle(
            intent = intent,
            eventId = eventId,
            inboundSmsHandler = param.thisObject,
            receiver = param.args.getOrNull(receiverIndex),
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
                    OperateSmsAction.FORCE_DELETE,
                ).call()
            }.onFailure {
                XLog.w("Diag sms blacklist delete task failed: %s", it.message ?: "unknown")
            }
        }
    }

    private fun ensureEventId(intent: Intent): String {
        return XpDispatchCoordinator.ensureIncomingEventId(intent)
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
        private val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
        private val SMS_OPERATION_EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
