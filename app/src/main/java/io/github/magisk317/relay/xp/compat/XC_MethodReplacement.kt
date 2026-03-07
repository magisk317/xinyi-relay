package io.github.magisk317.relay.xp.compat

abstract class XC_MethodReplacement : XC_MethodHook() {

    final override fun beforeHookedMethod(param: MethodHookParam) {
        param.setResult(replaceHookedMethod(param))
    }

    final override fun afterHookedMethod(param: MethodHookParam) {
        // no-op
    }

    protected abstract fun replaceHookedMethod(param: MethodHookParam): Any?

    companion object {
        fun returnConstant(result: Any?): XC_MethodReplacement {
            return object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? = result
            }
        }
    }
}
