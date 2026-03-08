package io.github.magisk317.relay.xp.compat

import java.lang.reflect.Field
import java.lang.reflect.Method

object XposedHelpers {

    @JvmStatic
    @Throws(ClassNotFoundException::class)
    fun findClass(className: String, classLoader: ClassLoader?): Class<*> {
        return if (classLoader == null) {
            Class.forName(className)
        } else {
            Class.forName(className, false, classLoader)
        }
    }

    @JvmStatic
    fun findClassIfExists(className: String, classLoader: ClassLoader?): Class<*>? {
        return runCatching { findClass(className, classLoader) }.getOrNull()
    }

    @JvmStatic
    @Throws(ClassNotFoundException::class)
    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        vararg parameterTypesAndCallback: Any,
    ): XC_MethodHook.Unhook {
        val clazz = findClass(className, classLoader)
        return findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)
    }

    @JvmStatic
    fun findAndHookMethod(
        clazz: Class<*>?,
        methodName: String,
        vararg parameterTypesAndCallback: Any,
    ): XC_MethodHook.Unhook {
        requireNotNull(clazz) { "clazz is null" }
        require(parameterTypesAndCallback.isNotEmpty()) { "no callback defined" }
        val callbackObj = parameterTypesAndCallback.last()
        require(callbackObj is XC_MethodHook) { "last argument must be XC_MethodHook" }

        val parameters = parameterTypesAndCallback.copyOfRange(0, parameterTypesAndCallback.size - 1)
        val method = findMethodBestMatch(clazz, methodName, *parameters)
        return XposedBridge.hookMethod(method, callbackObj)
    }

    @JvmStatic
    fun findMethodBestMatch(clazz: Class<*>?, methodName: String, vararg parameterTypes: Any?): Method {
        requireNotNull(clazz) { "clazz is null" }
        val expected = toParameterClasses(*parameterTypes)

        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.forEach { method ->
                if (method.name != methodName) return@forEach
                val actual = method.parameterTypes
                if (actual.size != expected.size) return@forEach
                if (!isAssignable(actual, expected)) return@forEach
                method.isAccessible = true
                return method
            }
            current = current.superclass
        }

        throw NoSuchMethodError("${clazz.name}#$methodName")
    }

    @JvmStatic
    fun findMethodExact(clazz: Class<*>?, methodName: String, vararg parameterTypes: Any?): Method {
        requireNotNull(clazz) { "clazz is null" }
        val parameterClasses = toParameterClasses(*parameterTypes)
        return runCatching {
            clazz.getDeclaredMethod(methodName, *parameterClasses).apply { isAccessible = true }
        }.getOrElse {
            throw NoSuchMethodError("${clazz.name}#$methodName")
        }
    }

    @JvmStatic
    fun findMethodExactIfExists(clazz: Class<*>?, methodName: String, vararg parameterTypes: Any?): Method? {
        return runCatching { findMethodExact(clazz, methodName, *parameterTypes) }.getOrNull()
    }

    @JvmStatic
    fun findMethodsByExactParameters(
        clazz: Class<*>?,
        returnType: Class<*>?,
        vararg parameterTypes: Class<*>?,
    ): Array<Method> {
        requireNotNull(clazz) { "clazz is null" }
        return clazz.declaredMethods
            .filter { method ->
                if (returnType != null && method.returnType != returnType) {
                    return@filter false
                }
                val actual = method.parameterTypes
                if (actual.size != parameterTypes.size) {
                    return@filter false
                }
                for (i in actual.indices) {
                    val expected = parameterTypes[i] ?: return@filter false
                    if (actual[i] != expected) return@filter false
                }
                true
            }
            .onEach { it.isAccessible = true }
            .toTypedArray()
    }

    @JvmStatic
    fun callMethod(obj: Any?, methodName: String, vararg args: Any?): Any? {
        requireNotNull(obj) { "obj == null" }
        val method = findMethodBestMatch(obj.javaClass, methodName, *args)
        return runCatching { method.invoke(obj, *args) }
            .getOrElse { throw RuntimeException(it) }
    }

    @JvmStatic
    fun callStaticMethod(clazz: Class<*>?, methodName: String, vararg args: Any?): Any? {
        requireNotNull(clazz) { "clazz == null" }
        val method = findMethodBestMatch(clazz, methodName, *args)
        return runCatching { method.invoke(null, *args) }
            .getOrElse { throw RuntimeException(it) }
    }

    @JvmStatic
    fun getObjectField(obj: Any?, fieldName: String): Any {
        requireNotNull(obj) { "obj == null" }
        val field = findField(obj.javaClass, fieldName)
        return runCatching { field.get(obj) }.getOrElse { throw RuntimeException(it) } as Any
    }

    @JvmStatic
    fun getIntField(obj: Any?, fieldName: String): Int {
        requireNotNull(obj) { "obj == null" }
        val field = findField(obj.javaClass, fieldName)
        return runCatching { field.getInt(obj) }.getOrElse { throw RuntimeException(it) }
    }

    @JvmStatic
    fun getStaticIntField(clazz: Class<*>?, fieldName: String): Int {
        requireNotNull(clazz) { "clazz == null" }
        val field = findField(clazz, fieldName)
        return runCatching { field.getInt(null) }.getOrElse { throw RuntimeException(it) }
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                current.getDeclaredField(fieldName).apply { isAccessible = true }
            }.onSuccess { return it }
            current = current.superclass
        }
        throw NoSuchFieldException("${clazz.name}#$fieldName")
    }

    private fun toParameterClasses(vararg parameterTypesOrValues: Any?): Array<Class<*>> {
        if (parameterTypesOrValues.isEmpty()) return emptyArray()
        return Array(parameterTypesOrValues.size) { index ->
            val item = parameterTypesOrValues[index]
            when (item) {
                is Class<*> -> item
                null -> Any::class.java
                else -> item.javaClass
            }
        }
    }

    private fun isAssignable(actual: Array<Class<*>>, expected: Array<Class<*>>): Boolean {
        for (i in actual.indices) {
            val expectedClass = wrapPrimitive(expected[i])
            val actualClass = wrapPrimitive(actual[i])
            if (expectedClass == Any::class.java) continue
            if (!actualClass.isAssignableFrom(expectedClass)) return false
        }
        return true
    }

    private fun wrapPrimitive(clazz: Class<*>): Class<*> {
        if (!clazz.isPrimitive) return clazz
        return when (clazz) {
            Int::class.javaPrimitiveType -> Int::class.javaObjectType
            Long::class.javaPrimitiveType -> Long::class.javaObjectType
            Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
            Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
            Short::class.javaPrimitiveType -> Short::class.javaObjectType
            Float::class.javaPrimitiveType -> Float::class.javaObjectType
            Double::class.javaPrimitiveType -> Double::class.javaObjectType
            Char::class.javaPrimitiveType -> Char::class.javaObjectType
            else -> clazz
        }
    }
}
