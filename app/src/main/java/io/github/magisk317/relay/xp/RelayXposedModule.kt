package io.github.magisk317.relay.xp

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.xp.compat.IXposedHookZygoteInit
import io.github.magisk317.relay.xp.compat.XposedRuntime
import io.github.magisk317.relay.xp.compat.callbacks.XC_LoadPackage
import java.util.concurrent.atomic.AtomicBoolean

class RelayXposedModule(
    base: XposedInterface,
    private val moduleLoadedParam: XposedModuleInterface.ModuleLoadedParam,
) : XposedModule(base, moduleLoadedParam) {

    private val hookEntry = HookEntry()
    private val dispatchedLoadKeys = mutableSetOf<String>()
    private val startupSelfCheckScheduled = AtomicBoolean(false)

    init {
        XposedRuntime.install(this)
        val startupParam = IXposedHookZygoteInit.StartupParam().apply {
            modulePath = runCatching { applicationInfo.sourceDir }.getOrNull()
            startsSystemServer = moduleLoadedParam.isSystemServer
        }
        hookEntry.initZygote(startupParam, this)

        XLog.i(
            "RelayXposedModule init: process=%s isSystemServer=%s sourceDir=%s modulePath=%s",
            moduleLoadedParam.processName,
            moduleLoadedParam.isSystemServer,
            runCatching { applicationInfo.sourceDir }.getOrNull() ?: "unknown",
            startupParam.modulePath ?: "unknown",
        )

        // Fallback for environments where system_server callback might be delayed or missed.
        if (moduleLoadedParam.isSystemServer) {
            dispatchLoadPackage(
                source = "init.systemServerFallback",
                packageName = "android",
                classLoader = this::class.java.classLoader,
            )
            scheduleDelayedSystemServerDispatches(
                triggerSource = "init",
                classLoader = this::class.java.classLoader,
            )
        } else {
            scheduleStartupSelfCheck()
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
        scheduleDelayedSystemServerDispatches(
            triggerSource = "onSystemServerLoaded",
            classLoader = param.classLoader,
        )
    }

    private fun scheduleDelayedSystemServerDispatches(triggerSource: String, classLoader: ClassLoader?) {
        if (!moduleLoadedParam.isSystemServer) return
        val delays = longArrayOf(2_000L, 8_000L)
        for (delayMs in delays) {
            Thread(
                {
                    runCatching {
                        Thread.sleep(delayMs)
                        dispatchLoadPackage(
                            source = "delayedSystemServerFallback($triggerSource,$delayMs)",
                            packageName = "android",
                            classLoader = classLoader,
                        )
                    }.onFailure {
                        XLog.e(
                            "RelayXposedModule delayed dispatch failed: trigger=%s delayMs=%s err=%s",
                            triggerSource,
                            delayMs,
                            it.message ?: it.javaClass.simpleName,
                        )
                        XLog.e("", it)
                    }
                },
                "xrelay-system-fallback-$delayMs",
            ).apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun scheduleStartupSelfCheck() {
        if (!startupSelfCheckScheduled.compareAndSet(false, true)) return
        val processName = moduleLoadedParam.processName.ifBlank { "<unknown>" }
        Thread(
            {
                runCatching {
                    Thread.sleep(STARTUP_SELF_CHECK_DELAY_MS)
                    XLog.w(
                        STARTUP_SELF_CHECK_WARNING,
                        processName,
                    )
                }.onFailure {
                    XLog.e(
                        "Startup self-check failed: process=%s err=%s",
                        processName,
                        it.message ?: it.javaClass.simpleName,
                    )
                }
            },
            "xrelay-startup-selfcheck",
        ).apply {
            isDaemon = true
            start()
        }
    }

    companion object {
        private const val STARTUP_SELF_CHECK_DELAY_MS = 12_000L
        private const val STARTUP_SELF_CHECK_WARNING =
            "Startup self-check: process=%s isSystemServer=false. " +
                "If no 'RelayXposedModule init ... isSystemServer=true' appears after reboot, " +
                "android/system scope is not injected and call/notification hooks will be unavailable this boot."
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
        runCatching {
            hookEntry.handleLoadPackage(lpparam, source)
        }.onFailure {
            XLog.e(
                "RelayXposedModule handleLoadPackage failed: source=%s pkg=%s process=%s err=%s",
                source,
                packageName,
                processName,
                it.message ?: it.javaClass.simpleName,
            )
            XLog.e("", it)
        }
    }
}
