package io.github.magisk317.relay.xp.hook.keepalive

import io.github.magisk317.smscode.xposed.hook.BaseHook
import io.github.magisk317.smscode.xposed.hookapi.HookBridge
import io.github.magisk317.smscode.xposed.hookapi.HookHelpers
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.MethodHook
import io.github.magisk317.smscode.xposed.hookapi.MethodHookParam
import io.github.magisk317.smscode.xposed.prefs.CorePrefs
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.smscode.xposed.utils.runNonFatalCatching
import io.github.magisk317.smscode.xposed.utils.runNonFatalOrNull
import java.lang.reflect.Method

class KeepAliveHook : BaseHook() {

    override fun hookOnLoadPackage(): Boolean = true

    override fun onLoadPackage(lpparam: LoadParam) {
        val isSystemPackage = lpparam.packageName == "android" || lpparam.packageName == "system"
        val isSystemProcess = lpparam.processName == "system" ||
            lpparam.processName == "android" ||
            lpparam.processName == "system_server"
        if (!isSystemPackage || !isSystemProcess) return

        XLog.i("KeepAliveHook: loading in system_server")

        hookOomAdjuster(lpparam)
        hookKillProcess(lpparam)
        hookAppStandbyController(lpparam)
        hookDeviceIdleController(lpparam)
    }

    // ── Hook 1: OomAdjuster ──────────────────────────────────────────────────

    private fun hookOomAdjuster(lpparam: LoadParam) {
        runNonFatalCatching {
            val oomAdjusterClass = HookHelpers.findClassIfExists(
                "com.android.server.am.OomAdjuster",
                lpparam.classLoader,
            ) ?: run {
                XLog.w("KeepAliveHook: OomAdjuster class not found")
                return
            }

            val targetMethod = findMethodByNames(oomAdjusterClass, "computeOomAdjLSP", "computeOomAdjLocked") ?: run {
                XLog.w("KeepAliveHook: no computeOomAdj method found")
                return
            }

            XLog.i("KeepAliveHook: hooking %s#%s", oomAdjusterClass.name, targetMethod.name)

            HookBridge.hookMethod(
                targetMethod,
                object : MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        runNonFatalCatching {
                            if (!readPrefEnabled(KeepAliveHookConst.KEY_KEEPALIVE_OOM_ADJ)) return
                            adjustOomAdjForTarget(param)
                        }.onFailure { t ->
                            XLog.e("KeepAliveHook: OomAdjuster hook error", t)
                        }
                    }
                },
            )
            XLog.w("KeepAliveHook: successfully hooked OomAdjuster")
        }.onFailure { t ->
            XLog.e("KeepAliveHook: failed to hook OomAdjuster", t)
        }
    }

    private fun adjustOomAdjForTarget(param: MethodHookParam) {
        for (arg in param.args) {
            if (arg == null) continue
            val processName = runNonFatalOrNull {
                HookHelpers.getObjectField(arg, "processName") as? String
            } ?: continue
            if (processName != KeepAliveHookConst.TARGET_PACKAGE) continue

            runNonFatalCatching {
                val adjFields = listOf("curAdj", "mCurAdj", "setAdj")
                for (field in adjFields) {
                    runNonFatalCatching {
                        val currentAdj = HookHelpers.getIntField(arg, field)
                        if (currentAdj > FOREGROUND_APP_ADJ) {
                            val f = arg.javaClass.getDeclaredField(field)
                            f.isAccessible = true
                            f.setInt(arg, FOREGROUND_APP_ADJ)
                            XLog.d(
                                "KeepAliveHook: set adj=%d for %s (field=%s, was=%d)",
                                FOREGROUND_APP_ADJ, processName, field, currentAdj,
                            )
                        }
                        return
                    }
                }
            }
            break
        }
    }

    // ── Hook 2: AMS Kill Process ─────────────────────────────────────────────

    private fun hookKillProcess(lpparam: LoadParam) {
        runNonFatalCatching {
            val amsClass = HookHelpers.findClassIfExists(
                "com.android.server.am.ActivityManagerService",
                lpparam.classLoader,
            ) ?: run {
                XLog.w("KeepAliveHook: AMS class not found")
                return
            }

            val killMethod = findMethodByNames(
                amsClass,
                "killProcessLocked",
                "cleanUpApplicationRecordLocked",
                "handleAppDiedLocked",
            ) ?: run {
                XLog.w("KeepAliveHook: no kill method found in AMS")
                return
            }

            XLog.i("KeepAliveHook: hooking AMS %s", killMethod.name)

            HookBridge.hookMethod(
                killMethod,
                object : MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        runNonFatalCatching {
                            if (!readPrefEnabled(KeepAliveHookConst.KEY_KEEPALIVE_ANTI_KILL)) return
                            if (shouldSkipKill(param)) {
                                param.result = null
                                XLog.w("KeepAliveHook: intercepted kill for %s", KeepAliveHookConst.TARGET_PACKAGE)
                            }
                        }.onFailure { t ->
                            XLog.e("KeepAliveHook: kill hook error", t)
                        }
                    }
                },
            )
            XLog.w("KeepAliveHook: successfully hooked AMS kill")
        }.onFailure { t ->
            XLog.e("KeepAliveHook: failed to hook AMS kill", t)
        }
    }

    private fun shouldSkipKill(param: MethodHookParam): Boolean {
        for (arg in param.args) {
            if (arg == null) continue
            val processName = runNonFatalOrNull {
                HookHelpers.getObjectField(arg, "processName") as? String
            }
            if (processName == KeepAliveHookConst.TARGET_PACKAGE) return true

            val pkgName = runNonFatalOrNull {
                HookHelpers.getObjectField(arg, "info")?.let {
                    HookHelpers.getObjectField(it, "packageName") as? String
                }
            }
            if (pkgName == KeepAliveHookConst.TARGET_PACKAGE) return true
        }
        return false
    }

    // ── Hook 3: AppStandbyController ─────────────────────────────────────────

    private fun hookAppStandbyController(lpparam: LoadParam) {
        runNonFatalCatching {
            val standbyClass = HookHelpers.findClassIfExists(
                "com.android.server.usage.AppStandbyController",
                lpparam.classLoader,
            ) ?: run {
                XLog.w("KeepAliveHook: AppStandbyController class not found")
                return
            }

            val bucketMethod = findMethodByNames(
                standbyClass,
                "setActiveBucket",
                "postMessage",
                "setAppStandbyBucket",
            ) ?: run {
                XLog.w("KeepAliveHook: no bucket method found")
                return
            }

            XLog.i("KeepAliveHook: hooking AppStandbyController %s", bucketMethod.name)

            HookBridge.hookMethod(
                bucketMethod,
                object : MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        runNonFatalCatching {
                            if (!readPrefEnabled(KeepAliveHookConst.KEY_KEEPALIVE_STANDBY_BYPASS)) return
                            overrideStandbyBucket(param)
                        }.onFailure { t ->
                            XLog.e("KeepAliveHook: standby hook error", t)
                        }
                    }
                },
            )
            XLog.w("KeepAliveHook: successfully hooked AppStandbyController")
        }.onFailure { t ->
            XLog.e("KeepAliveHook: failed to hook AppStandbyController", t)
        }
    }

    private fun overrideStandbyBucket(param: MethodHookParam) {
        var targetPkg: String? = null
        for (arg in param.args) {
            if (arg is String && targetPkg == null) {
                targetPkg = arg
            }
        }
        if (targetPkg != KeepAliveHookConst.TARGET_PACKAGE) return

        for (i in param.args.indices) {
            if (param.args[i] is Int) {
                val currentBucket = param.args[i] as Int
                if (currentBucket != STANDBY_BUCKET_ACTIVE) {
                    param.args[i] = STANDBY_BUCKET_ACTIVE
                    XLog.d(
                        "KeepAliveHook: forced standby bucket ACTIVE for %s (was %d)",
                        targetPkg, currentBucket,
                    )
                }
                break
            }
        }
    }

    // ── Hook 4: DeviceIdleController ─────────────────────────────────────────

    private fun hookDeviceIdleController(lpparam: LoadParam) {
        runNonFatalCatching {
            val idleClass = HookHelpers.findClassIfExists(
                "com.android.server.DeviceIdleController",
                lpparam.classLoader,
            ) ?: run {
                XLog.w("KeepAliveHook: DeviceIdleController class not found")
                return
            }

            val idleMethod = findMethodByNames(
                idleClass,
                "becomeActiveIfAppTempIdleLocked",
                "stepIdleStateLocked",
                "setAppIdleAsync",
            ) ?: run {
                XLog.w("KeepAliveHook: no idle method found")
                return
            }

            XLog.i("KeepAliveHook: hooking DeviceIdleController %s", idleMethod.name)

            HookBridge.hookMethod(
                idleMethod,
                object : MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        runNonFatalCatching {
                            if (!readPrefEnabled(KeepAliveHookConst.KEY_KEEPALIVE_DOZE_BYPASS)) return
                            bypassDozeForTarget(param)
                        }.onFailure { t ->
                            XLog.e("KeepAliveHook: doze hook error", t)
                        }
                    }
                },
            )
            XLog.w("KeepAliveHook: successfully hooked DeviceIdleController")
        }.onFailure { t ->
            XLog.e("KeepAliveHook: failed to hook DeviceIdleController", t)
        }
    }

    private fun bypassDozeForTarget(param: MethodHookParam) {
        for (arg in param.args) {
            if (arg is String && arg == KeepAliveHookConst.TARGET_PACKAGE) {
                param.result = null
                XLog.d("KeepAliveHook: bypassed doze for %s", KeepAliveHookConst.TARGET_PACKAGE)
                return
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun readPrefEnabled(key: String): Boolean {
        return runNonFatalCatching {
            CorePrefs.getBoolean(key, false)
        }.getOrElse { t ->
            XLog.w("KeepAliveHook: pref read failed key=%s err=%s", key, t.message)
            false
        }
    }

    private fun findMethodByNames(clazz: Class<*>, vararg names: String): Method? {
        for (name in names) {
            val methods = clazz.declaredMethods.filter { it.name == name }
            if (methods.isNotEmpty()) {
                methods.first().isAccessible = true
                return methods.first()
            }
        }
        return null
    }

    companion object {
        private const val FOREGROUND_APP_ADJ = 0
        private const val STANDBY_BUCKET_ACTIVE = 10
    }
}
