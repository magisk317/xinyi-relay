package io.github.magisk317.relay.xp.hook

import io.github.magisk317.relay.xp.compat.IXposedHookZygoteInit
import io.github.magisk317.relay.xp.compat.callbacks.XC_LoadPackage

interface IHook {

    @Throws(Throwable::class)
    fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam)

    @Throws(Throwable::class)
    fun onLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam)
}
