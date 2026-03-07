package io.github.magisk317.relay.xp.hook

import io.github.magisk317.relay.xp.compat.IXposedHookZygoteInit
import io.github.magisk317.relay.xp.compat.callbacks.XC_LoadPackage

open class BaseHook : IHook {

    @Throws(Throwable::class)
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
    }

    open fun hookInitZygote(): Boolean = false

    @Throws(Throwable::class)
    override fun onLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
    }

    open fun hookOnLoadPackage(): Boolean = true
}
