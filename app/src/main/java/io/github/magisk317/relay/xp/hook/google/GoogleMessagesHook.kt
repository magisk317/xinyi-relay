package io.github.magisk317.relay.xp.hook.google

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.common.utils.PrefsReader
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.xp.helper.XposedWrapper
import io.github.magisk317.relay.xp.hook.BaseHook
import io.github.magisk317.relay.xp.compat.XC_MethodHook
import io.github.magisk317.relay.xp.compat.callbacks.XC_LoadPackage
import java.util.concurrent.Executors
import java.util.regex.Pattern

class GoogleMessagesHook : BaseHook() {
    override fun onLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != GOOGLE_MESSAGES_PACKAGE_NAME) return
        bugleClassLoader = lpparam.classLoader
        XLog.i("GoogleMessagesHook initializing")
        hookReceiver(lpparam.classLoader, TELEPHONY_CHANGE_RECEIVER_CLASS)
        hookReceiver(lpparam.classLoader, SMS_DELIVER_RECEIVER_CLASS)
    }

    private fun hookReceiver(classLoader: ClassLoader, receiverClassName: String) {
        val receiverClass = XposedWrapper.findClass(receiverClassName, classLoader) ?: return
        var hookedCount = 0
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val context = param.args.getOrNull(0) as? Context ?: return
                val intent = param.args.getOrNull(1) as? Intent ?: return
                XLog.i("GoogleMessagesHook callback from ${param.method.declaringClass.simpleName}.${param.method.name}, action=${intent.action}")
                if (!shouldHandle(intent.action)) return
                maybeSyncUnreadOtp(context)
            }
        }

        val visited = linkedSetOf<Class<*>>()
        var current: Class<*>? = receiverClass
        while (current != null && current != Any::class.java && visited.add(current)) {
            current.declaredMethods.forEach { method ->
                val types = method.parameterTypes
                if (types.size == 2 &&
                    Context::class.java.isAssignableFrom(types[0]) &&
                    Intent::class.java.isAssignableFrom(types[1])
                ) {
                    XposedWrapper.hookMethod(method, callback)
                    hookedCount += 1
                    XLog.i("GoogleMessagesHook hooked method: ${current.simpleName}.${method.name}(${types[0].simpleName},${types[1].simpleName})")
                }
            }
            current = current.superclass
        }
        XLog.i("GoogleMessagesHook hooked receiver: $receiverClassName, methods=$hookedCount")
    }

    private fun shouldHandle(action: String?): Boolean {
        return action == Telephony.Sms.Intents.SMS_DELIVER_ACTION ||
            action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
            action == Telephony.Sms.Intents.ACTION_EXTERNAL_PROVIDER_CHANGE
    }

    private fun maybeSyncUnreadOtp(context: Context) {
        val now = System.currentTimeMillis()
        synchronized(syncLock) {
            if (now - lastSyncAtMs < SYNC_DEBOUNCE_MS) return
            lastSyncAtMs = now
        }
        XLog.i("GoogleMessagesHook schedule sync")
        workExecutor.execute {
            try {
                val pluginContext = context.createPackageContext(
                    SMSCODE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
                syncRecentUnreadOtp(context, pluginContext)
            } catch (e: Throwable) {
                XLog.w("GoogleMessagesHook sync failed", e)
            }
        }
    }

    private fun syncRecentUnreadOtp(hostContext: Context, pluginContext: Context) {
        val keywordsRegex = PrefsReader.getSMSCodeKeywords(pluginContext).orEmpty()
        if (keywordsRegex.isBlank()) return
        val keywordPattern = runCatching { Pattern.compile(keywordsRegex, Pattern.CASE_INSENSITIVE) }.getOrNull() ?: return
        val codePattern = Pattern.compile("(?<![a-zA-Z0-9])[a-zA-Z0-9]{4,8}(?![a-zA-Z0-9])")

        val cutoff = System.currentTimeMillis() - RECENT_SMS_WINDOW_MS
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.BODY,
            Telephony.Sms.READ,
            Telephony.Sms.DATE,
        )
        val selection = "${Telephony.Sms.READ}=0 AND ${Telephony.Sms.TYPE}=? AND ${Telephony.Sms.DATE}>?"
        val selectionArgs = arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), cutoff.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC limit $MAX_RECENT_SMS_COUNT"

        val threadIds = linkedSetOf<Long>()
        val smsIds = mutableListOf<Long>()
        var updatedRows = 0
        var scannedRows = 0
        hostContext.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                scannedRows += 1
                val smsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID))
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty()
                if (!isLikelyOtp(body, keywordPattern, codePattern)) continue
                smsIds.add(smsId)
                if (threadId > 0) threadIds.add(threadId)
                val rows = hostContext.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    ContentValues().apply {
                        put(Telephony.Sms.READ, true)
                        put(Telephony.Sms.SEEN, true)
                    },
                    "${Telephony.Sms._ID}=?",
                    arrayOf(smsId.toString()),
                )
                if (rows > 0) {
                    updatedRows += rows
                }
            }
        }
        XLog.i(
            "GoogleMessagesHook scanned=$scannedRows otpCandidates=${smsIds.size} updated=$updatedRows threads=${threadIds.size}",
        )

        if (smsIds.isNotEmpty()) {
            tryInvokeBugleMarkAsRead(hostContext, threadIds, smsIds)
        }
        threadIds.forEach { threadId ->
            hostContext.contentResolver.update(
                Uri.parse("$MMS_SMS_CONVERSATIONS_URI/$threadId"),
                ContentValues().apply {
                    put(CONVERSATION_READ_COLUMN, 1)
                },
                null,
                null,
            )
        }
        if (updatedRows > 0) {
            XLog.i("GoogleMessagesHook marked $updatedRows OTP SMS as read, threads=${threadIds.size}")
        } else if (BuildConfig.DEBUG) {
            XLog.d("GoogleMessagesHook no unread OTP SMS to sync")
        }
    }

    private fun tryInvokeBugleMarkAsRead(context: Context, threadIds: Set<Long>, smsIds: List<Long>) {
        val classLoader = bugleClassLoader ?: return
        val firstSmsUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, smsIds.first().toString())
        val smsUriList = smsIds.map { Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, it.toString()) }
        val threadIdStrings = threadIds.map { it.toString() }
        var invoked = false

        MARK_AS_READ_CLASS_NAMES.forEach { className ->
            val clazz = XposedWrapper.findClass(className, classLoader) ?: return@forEach
            XLog.i("GoogleMessagesHook probing class: $className, methods=${clazz.declaredMethods.size}")
            clazz.declaredMethods.forEach methodLoop@{ method ->
                val name = method.name.lowercase()
                if (!name.contains("mark") || !name.contains("read")) return@methodLoop
                val methodSig = "${clazz.simpleName}#${method.name}(${method.parameterTypes.joinToString { it.simpleName }})"
                val args = buildArgsForMethod(
                    method.parameterTypes,
                    context,
                    threadIds.firstOrNull(),
                    firstSmsUri,
                    smsUriList,
                    threadIdStrings,
                ) ?: return@methodLoop
                runCatching {
                    method.isAccessible = true
                    val target = if (java.lang.reflect.Modifier.isStatic(method.modifiers)) null else {
                        runCatching { clazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance() }.getOrNull()
                            ?: return@runCatching
                    }
                    method.invoke(target, *args)
                    invoked = true
                    XLog.i("GoogleMessagesHook invoked Bugle mark-read: $methodSig")
                }.onFailure {
                    XLog.i("GoogleMessagesHook invoke failed: $methodSig, err=${it.javaClass.simpleName}")
                }
            }
        }

        if (!invoked) {
            XLog.w("GoogleMessagesHook cannot invoke Bugle internal mark-read method")
        }
    }

    private fun buildArgsForMethod(
        parameterTypes: Array<Class<*>>,
        context: Context,
        firstThreadId: Long?,
        firstSmsUri: Uri,
        smsUriList: List<Uri>,
        threadIdStrings: List<String>,
    ): Array<Any?>? {
        val args = arrayOfNulls<Any>(parameterTypes.size)
        parameterTypes.forEachIndexed { index, clazz ->
            val value: Any? = when {
                Context::class.java.isAssignableFrom(clazz) -> context
                clazz == Long::class.javaPrimitiveType || clazz == Long::class.javaObjectType -> firstThreadId ?: 0L
                clazz == Int::class.javaPrimitiveType || clazz == Int::class.javaObjectType -> 0
                clazz == Boolean::class.javaPrimitiveType || clazz == Boolean::class.javaObjectType -> true
                clazz == String::class.java -> {
                    if (threadIdStrings.isNotEmpty()) threadIdStrings.first() else firstSmsUri.toString()
                }
                Uri::class.java.isAssignableFrom(clazz) -> firstSmsUri
                List::class.java.isAssignableFrom(clazz) || Collection::class.java.isAssignableFrom(clazz) -> smsUriList
                Set::class.java.isAssignableFrom(clazz) -> smsUriList.toSet()
                clazz.isArray && clazz.componentType == Uri::class.java -> smsUriList.toTypedArray()
                else -> return null
            }
            args[index] = value
        }
        return args
    }

    private fun isLikelyOtp(body: String, keywordPattern: Pattern, codePattern: Pattern): Boolean {
        if (body.isBlank()) return false
        return keywordPattern.matcher(body).find() && codePattern.matcher(body).find()
    }

    companion object {
        private const val GOOGLE_MESSAGES_PACKAGE_NAME = "com.google.android.apps.messaging"
        private const val TELEPHONY_CHANGE_RECEIVER_CLASS =
            "com.google.android.apps.messaging.shared.receiver.TelephonyChangeReceiver"
        private const val SMS_DELIVER_RECEIVER_CLASS =
            "com.google.android.apps.messaging.shared.receiver.SmsDeliverReceiver"
        private const val SYNC_DEBOUNCE_MS = 500L
        private const val RECENT_SMS_WINDOW_MS = 3 * 60 * 1000L
        private const val MAX_RECENT_SMS_COUNT = 24
        private const val MMS_SMS_CONVERSATIONS_URI = "content://mms-sms/conversations"
        private const val CONVERSATION_READ_COLUMN = "read"
        private val MARK_AS_READ_CLASS_NAMES = listOf(
            "com.google.android.apps.messaging.shared.datamodel.action.MarkAsReadAction",
            "com.google.android.apps.messaging.shared.api.messaging.control.markasread.MarkMessagesAsReadHandler",
        )
        private val workExecutor = Executors.newSingleThreadExecutor()
        private val syncLock = Any()
        @Volatile
        private var lastSyncAtMs = 0L
        @Volatile
        private var bugleClassLoader: ClassLoader? = null
        private val SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID
    }
}
