package io.github.magisk317.relay.xp.hook.code

import android.os.Binder
import android.os.Message
import io.github.magisk317.smscode.core.hookapi.HookHelpers
import io.github.magisk317.smscode.core.utils.XLog
import java.lang.reflect.Method
import java.util.ArrayDeque

internal class InboundSmsBlocker(
    private val smsHandlerClassName: String,
) {
    fun blockInboundSms(
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
        deleteFromRawTable24(inboundSmsHandler, smsReceiver, reason, eventId)
    }

    @Throws(ReflectiveOperationException::class)
    private fun deleteFromRawTable24(inboundSmsHandler: Any, smsReceiver: Any, reason: String, eventId: String) {
        XLog.d("Delete raw SMS data from database on Android 24+: reason=%s event_id=%s", reason, eventId)
        val deleteWhere = HookHelpers.getObjectField(smsReceiver, "mDeleteWhere")
        val deleteWhereArgs = HookHelpers.getObjectField(smsReceiver, "mDeleteWhereArgs")
        val markDeleted = 2
        val handlerClass = HookHelpers.findClass(smsHandlerClassName, inboundSmsHandler.javaClass.classLoader)
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

    companion object {
        private const val EVENT_BROADCAST_COMPLETE = 3
        private const val PERSISTENT_DEVICE_ID_DEFAULT = "default:0"

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
