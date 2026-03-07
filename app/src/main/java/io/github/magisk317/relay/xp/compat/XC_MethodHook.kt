package io.github.magisk317.relay.xp.compat

import java.lang.reflect.Member

abstract class XC_MethodHook {

    @Throws(Throwable::class)
    protected open fun beforeHookedMethod(param: MethodHookParam) {
    }

    @Throws(Throwable::class)
    protected open fun afterHookedMethod(param: MethodHookParam) {
    }

    internal fun dispatchBefore(param: MethodHookParam) {
        beforeHookedMethod(param)
    }

    internal fun dispatchAfter(param: MethodHookParam) {
        afterHookedMethod(param)
    }

    class MethodHookParam {
        lateinit var method: Member
        var thisObject: Any = Any()
        var args: Array<Any?> = emptyArray()
        var returnEarly: Boolean = false

        private var _result: Any? = null
        private var _throwable: Throwable? = null

        fun getResult(): Any? = _result

        fun setResult(value: Any?) {
            _result = value
            _throwable = null
            returnEarly = true
        }

        fun getThrowable(): Throwable? = _throwable

        fun setThrowable(value: Throwable?) {
            _throwable = value
            _result = null
            returnEarly = true
        }

        fun hasThrowable(): Boolean = _throwable != null

        @Throws(Throwable::class)
        fun getResultOrThrowable(): Any? {
            _throwable?.let { throw it }
            return _result
        }
    }

    class Unhook(
        private val hookedMethod: Member,
        private val action: (() -> Unit)? = null,
    ) {
        fun getHookedMethod(): Member = hookedMethod

        fun unhook() {
            action?.invoke()
        }
    }
}
