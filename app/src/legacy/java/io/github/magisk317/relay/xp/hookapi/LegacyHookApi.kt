package io.github.magisk317.relay.xp.hookapi

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.magisk317.smscode.xposed.hookapi.HookApi
import io.github.magisk317.smscode.xposed.hookapi.HookHandle
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam
import io.github.magisk317.smscode.xposed.utils.XLog
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class LegacyHookApi : HookApi {
    override fun hookMethod(member: Member, callback: MethodHook): HookHandle? {
        if (member is Method && (Modifier.isAbstract(member.modifiers) || Modifier.isNative(member.modifiers))) {
            XLog.d("Skip hook unsupported method: %s#%s", member.declaringClass.name, member.name)
            return null
        }
        val unhook = runCatching {
            XposedBridge.hookMethod(member, LegacyCallback(callback))
        }.getOrNull() ?: return null
        return LegacyHookHandle(unhook)
    }

    override fun hookAllConstructors(clazz: Class<*>, callback: MethodHook): Set<HookHandle> {
        val hooks = runCatching {
            XposedBridge.hookAllConstructors(clazz, LegacyCallback(callback))
        }.getOrDefault(emptySet())
        return hooks.map { LegacyHookHandle(it) }.toSet()
    }

    override fun hookAllMethods(clazz: Class<*>, methodName: String, callback: MethodHook): Set<HookHandle> {
        val hooks = runCatching {
            XposedBridge.hookAllMethods(clazz, methodName, LegacyCallback(callback))
        }.getOrDefault(emptySet())
        return hooks.map { LegacyHookHandle(it) }.toSet()
    }

    override fun log(priority: Int, tag: String?, msg: String, tr: Throwable?) {
        if (tr != null) {
            XposedBridge.log("${tag ?: "relay"}: $msg")
            XposedBridge.log(tr)
        } else {
            XposedBridge.log("${tag ?: "relay"}: $msg")
        }
    }

    override fun getApiVersion(): Int? = null

    override fun getFrameworkName(): String? = null

    override fun getFrameworkVersionCode(): Long? = null

    override fun getXposedBridgeVersion(): Int? {
        return runCatching {
            val method = XposedBridge::class.java.getMethod("getXposedVersion")
            (method.invoke(null) as? Number)?.toInt()
        }.getOrNull() ?: runCatching {
            val field = XposedBridge::class.java.getDeclaredField("XPOSED_BRIDGE_VERSION")
            field.isAccessible = true
            (field.get(null) as? Number)?.toInt()
        }.getOrNull()
    }

    private class LegacyCallback(private val callback: MethodHook) : XC_MethodHook() {
        override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
            callback.beforeHookedMethod(LegacyMethodHookParam(param))
        }

        override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
            callback.afterHookedMethod(LegacyMethodHookParam(param))
        }
    }

    private class LegacyMethodHookParam(private val param: XC_MethodHook.MethodHookParam) : MethodHookParam() {
        override val method: Member
            get() = param.method
        override var thisObject: Any?
            get() = param.thisObject
            set(value) {
                param.thisObject = value
            }
        override var args: Array<Any?>
            get() = param.args
            set(value) {
                param.args = value
            }
        override var result: Any?
            get() = param.result
            set(value) {
                param.result = value
                param.throwable = null
                returnEarly = true
            }
        override var throwable: Throwable?
            get() = param.throwable
            set(value) {
                param.throwable = value
                returnEarly = true
            }
    }

    private class LegacyHookHandle(private val unhook: XC_MethodHook.Unhook) : HookHandle {
        override fun unhook() {
            unhook.unhook()
        }
    }
}
