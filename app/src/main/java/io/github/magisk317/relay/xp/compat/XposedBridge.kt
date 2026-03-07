package io.github.magisk317.relay.xp.compat

import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object XposedBridge {

    @JvmStatic
    var XPOSED_BRIDGE_VERSION: Int = 0

    @JvmStatic
    fun getXposedVersion(): Int = XPOSED_BRIDGE_VERSION

    @JvmStatic
    fun log(text: String) {
        XposedRuntime.log(text)
    }

    @JvmStatic
    fun log(throwable: Throwable) {
        XposedRuntime.log(throwable)
    }

    @JvmStatic
    fun hookMethod(hookMethod: Member?, callback: XC_MethodHook): XC_MethodHook.Unhook {
        requireNotNull(hookMethod) { "hookMethod is null" }
        return XposedRuntime.hook(hookMethod, callback)
    }

    @JvmStatic
    fun hookAllMethods(
        hookClass: Class<*>?,
        methodName: String,
        callback: XC_MethodHook,
    ): Set<XC_MethodHook.Unhook> {
        requireNotNull(hookClass) { "hookClass is null" }
        return hookClass.declaredMethods
            .asSequence()
            .filter { method ->
                method.name == methodName && !Modifier.isAbstract(method.modifiers) && !Modifier.isNative(method.modifiers)
            }
            .map { hookMethod(it, callback) }
            .toSet()
    }

    @JvmStatic
    fun hookAllConstructors(
        hookClass: Class<*>?,
        callback: XC_MethodHook,
    ): Set<XC_MethodHook.Unhook> {
        requireNotNull(hookClass) { "hookClass is null" }
        return hookClass.declaredConstructors
            .map { constructor -> hookMethod(constructor, callback) }
            .toSet()
    }
}
