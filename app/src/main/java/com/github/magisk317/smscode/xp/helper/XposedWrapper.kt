package com.github.magisk317.smscode.xp.helper

import com.github.magisk317.smscode.common.utils.XLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.Unhook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Xposed Wrapper Utils
 */
object XposedWrapper {
    fun findClass(className: String, classLoader: ClassLoader): Class<*>? = try {
        XposedHelpers.findClass(className, classLoader)
    } catch (ignored: Throwable) {
        XLog.e("Class not found: %s", className)
        null
    }

    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader,
        methodName: String,
        vararg parameterTypesAndCallback: Any,
    ): Unhook? = try {
        XposedHelpers.findAndHookMethod(
            className,
            classLoader,
            methodName,
            *parameterTypesAndCallback,
        )
    } catch (t: Throwable) {
        XLog.e("Error in hook %s#%s", className, methodName, t)
        null
    }

    fun findAndHookMethod(clazz: Class<*>, methodName: String, vararg parameterTypesAndCallback: Any): Unhook? = try {
        XposedHelpers.findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)
    } catch (t: Throwable) {
        XLog.e("Error in hook %s#%s", clazz.name, methodName, t)
        null
    }

    fun hookMethod(hookMethod: Member, callback: XC_MethodHook): Unhook? = try {
        if (hookMethod is Method && (Modifier.isAbstract(hookMethod.modifiers) || Modifier.isNative(hookMethod.modifiers))) {
            XLog.d("Skip hook unsupported method: %s#%s", hookMethod.declaringClass.name, hookMethod.name)
            return null
        }
        XposedBridge.hookMethod(hookMethod, callback)
    } catch (t: Throwable) {
        XLog.e("Error in hookMethod: %s", hookMethod.name, t)
        null
    }

    fun hookAllConstructors(hookClass: Class<*>, callback: XC_MethodHook): Set<Unhook>? = try {
        XposedBridge.hookAllConstructors(hookClass, callback)
    } catch (t: Throwable) {
        XLog.e("Error in hookAllConstructors: %s", hookClass.name, t)
        null
    }

    fun hookAllMethods(hookClass: Class<*>, methodName: String, callback: XC_MethodHook): Set<Unhook>? = try {
        XposedBridge.hookAllMethods(hookClass, methodName, callback)
    } catch (t: Throwable) {
        XLog.e("Error in hookAllMethods: %s", hookClass.name, t)
        null
    }
}
