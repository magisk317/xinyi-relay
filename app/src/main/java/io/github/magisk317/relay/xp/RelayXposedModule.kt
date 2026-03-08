package io.github.magisk317.relay.xp

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.xp.compat.IXposedHookZygoteInit
import io.github.magisk317.relay.xp.compat.XposedRuntime
import io.github.magisk317.relay.xp.compat.callbacks.XC_LoadPackage

class RelayXposedModule(
    base: XposedInterface,
    private val moduleLoadedParam: XposedModuleInterface.ModuleLoadedParam,
) : XposedModule(base, moduleLoadedParam) {

    private val hookEntry = HookEntry()
    private val dispatchedLoadKeys = mutableSetOf<String>()

    init {
        XposedRuntime.install(this)
        val startupParam = IXposedHookZygoteInit.StartupParam().apply {
            modulePath = runCatching { applicationInfo.sourceDir }.getOrNull()
            startsSystemServer = moduleLoadedParam.isSystemServer
        }
        hookEntry.initZygote(startupParam, this)

        XLog.i(
            "RelayXposedModule init: process=%s isSystemServer=%s",
            moduleLoadedParam.processName,
            moduleLoadedParam.isSystemServer,
        )

        // Fallback for environments where system_server callback might be delayed or missed.
        if (moduleLoadedParam.isSystemServer) {
            dispatchLoadPackage(
                source = "init.systemServerFallback",
                packageName = "android",
                classLoader = this::class.java.classLoader,
            )
        }
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        dispatchLoadPackage(
            source = "onPackageLoaded",
            packageName = param.packageName,
            classLoader = param.classLoader,
        )
    }

    override fun onSystemServerLoaded(param: XposedModuleInterface.SystemServerLoadedParam) {
        dispatchLoadPackage(
            source = "onSystemServerLoaded",
            packageName = "android",
            classLoader = param.classLoader,
        )
    }

    private fun dispatchLoadPackage(source: String, packageName: String, classLoader: ClassLoader?) {
        val processName = moduleLoadedParam.processName.ifBlank {
            if (moduleLoadedParam.isSystemServer) "system_server" else "<unknown>"
        }
        val dedupKey = "$packageName|$processName|${System.identityHashCode(classLoader)}"
        val isFirstDispatch = synchronized(dispatchedLoadKeys) { dispatchedLoadKeys.add(dedupKey) }
        if (!isFirstDispatch) {
            XLog.d(
                "RelayXposedModule skip duplicate dispatch: source=%s pkg=%s process=%s loader=%s",
                source,
                packageName,
                processName,
                classLoader?.javaClass?.name ?: "null",
            )
            return
        }

        XLog.w(
            "RelayXposedModule dispatch: source=%s pkg=%s process=%s isSystemServer=%s loader=%s",
            source,
            packageName,
            processName,
            moduleLoadedParam.isSystemServer,
            classLoader?.javaClass?.name ?: "null",
        )

        val lpparam = XC_LoadPackage.LoadPackageParam().apply {
            this.packageName = packageName
            this.processName = processName
            this.classLoader = classLoader
                ?: this@RelayXposedModule::class.java.classLoader
                ?: ClassLoader.getSystemClassLoader()
        }
        hookEntry.handleLoadPackage(lpparam, source)
    }
}
