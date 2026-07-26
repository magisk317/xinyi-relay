package io.github.magisk317.relay.xp.hook.telephony

import android.content.ContentValues
import android.content.Context
import android.os.Binder
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.xp.HookTargetDiagnostics
import io.github.magisk317.relay.xpbridge.XpHookDiagnostics
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderHookInstaller
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.logging.MagiskOtel

class SmsProviderHook : BaseHook() {
    override fun hookOnLoadPackage(): Boolean = true

    override fun onLoadPackage(param: LoadParam) {
        XLog.withRoute(LogRoute.SMS_HOOK) {
            onLoadPackageRouted(param)
        }
    }

    private fun onLoadPackageRouted(param: LoadParam) {
        if (param.packageName != SmsProviderHookInstaller.TARGET_PACKAGE) return
        HookTargetDiagnostics.logTargetProcessHitIfVerbose(
            hookName = "SmsProviderHook",
            loadParam = param,
            targetPackage = SmsProviderHookInstaller.TARGET_PACKAGE,
        )
        val installation = SmsProviderHookInstaller.install(param.classLoader) { call ->
            XLog.withRoute(LogRoute.SMS_HOOK) { handleWrite(call) }
        }
        if (!installation.classFound) {
            XLog.w("SmsProviderHook: class not found: %s", SmsProviderHookInstaller.TARGET_CLASS)
            HookTargetDiagnostics.logTargetMissIfVerbose(
                hookName = "SmsProviderHook",
                loadParam = param,
                reason = "class_not_found",
                detail = SmsProviderHookInstaller.TARGET_CLASS,
            )
            recordLoad(result = "skip", reason = "class_missing")
            return
        }
        recordLoad(result = "ok", reason = "installed")
    }

    private fun handleWrite(call: SmsProviderHookInstaller.WriteCall) {
        val context = call.context
        val pluginContext = runCatching {
            context?.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull()
        if (pluginContext != null && context != null) {
            XpHookDiagnostics.recordSmsHookHeartbeat(
                context = pluginContext,
                packageName = SmsProviderHookInstaller.TARGET_PACKAGE,
                processName = context.applicationInfo?.processName ?: SmsProviderHookInstaller.TARGET_PACKAGE,
                source = "sms_provider_${call.methodName}",
                verboseLogging = XpPrefs.isVerboseLogMode(pluginContext),
            )
        }
        val callingUid = Binder.getCallingUid()
        val callingPid = Binder.getCallingPid()
        val packages = runCatching {
            context?.packageManager?.getPackagesForUid(callingUid)?.toList()
        }.getOrNull().orEmpty()
        val valuesSummary = when (call.methodName) {
            "insert", "update" -> {
                val values = call.hookParam.args.getOrNull(1) as? ContentValues
                values?.keySet()?.joinToString(",") ?: "<none>"
            }
            "bulkInsert" -> {
                val values = call.hookParam.args.getOrNull(1) as? Array<*>
                "count=${values?.size ?: 0}"
            }
            else -> "<none>"
        }
        XLog.w(
            "Diag sms provider %s: uri=%s uid=%d pid=%d pkgs=%s values=%s",
            call.methodName,
            call.uri,
            callingUid,
            callingPid,
            if (packages.isEmpty()) "<none>" else packages.joinToString(","),
            valuesSummary,
        )
    }

    private fun recordLoad(result: String, reason: String) {
        MagiskOtel.event(
            name = "hook.load",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "sms_provider",
                "reason" to reason,
                "target_package" to SmsProviderHookInstaller.TARGET_PACKAGE,
            ),
            statusOk = true,
        )
    }
}
