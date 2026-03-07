package io.github.magisk317.relay.xp

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.magisk317.relay.xp.compat.IXposedHookZygoteInit
import io.github.magisk317.relay.xp.compat.XposedRuntime
import io.github.magisk317.relay.xp.compat.callbacks.XC_LoadPackage

class RelayXposedModule(
    base: XposedInterface,
    private val moduleLoadedParam: XposedModuleInterface.ModuleLoadedParam,
) : XposedModule(base, moduleLoadedParam) {

    private val hookEntry = HookEntry()

    init {
        XposedRuntime.install(this)
        val startupParam = IXposedHookZygoteInit.StartupParam().apply {
            modulePath = runCatching { applicationInfo.sourceDir }.getOrNull()
            startsSystemServer = moduleLoadedParam.isSystemServer
        }
        hookEntry.initZygote(startupParam, this)
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        val lpparam = XC_LoadPackage.LoadPackageParam().apply {
            packageName = param.packageName
            processName = moduleLoadedParam.processName
            classLoader = param.classLoader
        }
        hookEntry.handleLoadPackage(lpparam)
    }

    override fun onSystemServerLoaded(param: XposedModuleInterface.SystemServerLoadedParam) {
        val lpparam = XC_LoadPackage.LoadPackageParam().apply {
            packageName = "android"
            processName = moduleLoadedParam.processName.ifBlank { "system_server" }
            classLoader = param.classLoader
        }
        hookEntry.handleLoadPackage(lpparam)
    }
}
