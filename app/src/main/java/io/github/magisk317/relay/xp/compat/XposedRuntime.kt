package io.github.magisk317.relay.xp.compat

import io.github.libxposed.api.XposedInterface
import io.github.magisk317.relay.common.utils.XLog
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal object XposedRuntime {
    private data class HookState(
        val member: Member,
        val callbacks: MutableList<XC_MethodHook>,
        var unhooker: XposedInterface.MethodUnhooker<*>? = null,
    )

    class InvocationContext(
        val param: XC_MethodHook.MethodHookParam,
        val callbacks: List<XC_MethodHook>,
    )

    private val states: MutableMap<Member, HookState> = ConcurrentHashMap()

    @Volatile
    private var base: XposedInterface? = null

    private val lock = Any()

    fun install(base: XposedInterface) {
        this.base = base
        val version = runCatching {
            base.getFrameworkVersionCode().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        }.getOrDefault(0)
        XposedBridge.XPOSED_BRIDGE_VERSION = version
    }

    fun current(): XposedInterface? = base

    fun log(text: String) {
        runCatching { base?.log(text) }
            .onFailure { XLog.w("XposedRuntime log failed: %s", it.message ?: "unknown") }
        XLog.i("%s", text)
    }

    fun log(throwable: Throwable) {
        runCatching { base?.log(throwable.message ?: "", throwable) }
            .onFailure { XLog.w("XposedRuntime throwable log failed: %s", it.message ?: "unknown") }
        XLog.e("XposedRuntime throwable", throwable)
    }

    fun hook(member: Member, callback: XC_MethodHook): XC_MethodHook.Unhook {
        synchronized(lock) {
            val state = states.getOrPut(member) { HookState(member = member, callbacks = mutableListOf()) }
            state.callbacks.add(callback)
            if (state.unhooker == null) {
                state.unhooker = registerWithFramework(member)
            }
        }
        return XC_MethodHook.Unhook(member) {
            remove(member, callback)
        }
    }

    private fun remove(member: Member, callback: XC_MethodHook) {
        synchronized(lock) {
            val state = states[member] ?: return
            state.callbacks.remove(callback)
            if (state.callbacks.isEmpty()) {
                runCatching { state.unhooker?.unhook() }
                states.remove(member)
            }
        }
    }

    private fun registerWithFramework(member: Member): XposedInterface.MethodUnhooker<*> {
        val base = requireNotNull(base) { "Xposed runtime is not installed" }
        return when (member) {
            is Method -> base.hook(member, DispatchHooker::class.java)
            is Constructor<*> -> {
                @Suppress("UNCHECKED_CAST")
                base.hook(member as Constructor<Any>, DispatchHooker::class.java)
            }
            else -> error("Unsupported member type: ${member.javaClass.name}")
        }
    }

    private fun snapshotCallbacks(member: Member): List<XC_MethodHook> {
        return synchronized(lock) {
            states[member]?.callbacks?.toList() ?: emptyList()
        }
    }

    fun before(callback: XposedInterface.BeforeHookCallback): InvocationContext {
        val param = XC_MethodHook.MethodHookParam().apply {
            method = callback.member
            thisObject = callback.thisObject ?: Any()
            @Suppress("UNCHECKED_CAST")
            args = callback.args as Array<Any?>
        }
        val callbacks = snapshotCallbacks(callback.member)
        callbacks.forEach { item ->
            item.dispatchBefore(param)
            if (param.returnEarly) {
                if (param.hasThrowable()) {
                    callback.throwAndSkip(param.getThrowable())
                } else {
                    callback.returnAndSkip(param.getResult())
                }
                return@forEach
            }
        }
        return InvocationContext(param, callbacks)
    }

    fun after(callback: XposedInterface.AfterHookCallback, context: InvocationContext?) {
        val param = context?.param ?: XC_MethodHook.MethodHookParam().apply {
            method = callback.member
            thisObject = callback.thisObject ?: Any()
            @Suppress("UNCHECKED_CAST")
            args = callback.args as Array<Any?>
        }

        if (callback.throwable != null) {
            param.setThrowable(callback.throwable)
        } else {
            param.setResult(callback.result)
            param.returnEarly = false
        }

        val callbacks = context?.callbacks ?: snapshotCallbacks(callback.member)
        for (i in callbacks.indices.reversed()) {
            callbacks[i].dispatchAfter(param)
        }

        if (param.hasThrowable()) {
            callback.setThrowable(param.getThrowable())
        } else {
            callback.setResult(param.getResult())
        }
    }
}

@Suppress("unused")
class DispatchHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        fun before(callback: XposedInterface.BeforeHookCallback): Any? {
            return runCatching { XposedRuntime.before(callback) }
                .onFailure { XposedRuntime.log(it) }
                .getOrNull()
        }

        @JvmStatic
        fun after(callback: XposedInterface.AfterHookCallback, context: Any?) {
            runCatching {
                XposedRuntime.after(callback, context as? XposedRuntime.InvocationContext)
            }.onFailure {
                XposedRuntime.log(it)
            }
        }
    }
}
