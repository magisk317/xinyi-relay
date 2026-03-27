package io.github.magisk317.relay.xp

import android.content.Context
import android.util.Log
import io.github.magisk317.relay.BuildConfig
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.utils.XLog

internal object HookTargetDiagnostics {
    data class ProbeResult(
        val candidateReasons: List<String>,
        val matchedTargets: List<String>,
    ) {
        val isCandidate: Boolean = candidateReasons.isNotEmpty() || matchedTargets.isNotEmpty()
        val missReason: String? = if (matchedTargets.isEmpty() && candidateReasons.isNotEmpty()) {
            "candidate_only"
        } else {
            null
        }
    }

    fun describePackageProbe(
        packageName: String,
        processName: String,
    ): ProbeResult {
        val packageLower = packageName.lowercase()
        val processLower = processName.lowercase()
        val candidateReasons = linkedSetOf<String>()
        val matchedTargets = linkedSetOf<String>()

        when (packageName) {
            ANDROID_PHONE_PACKAGE -> matchedTargets += "sms_handler,sms_forward"
            TELEPHONY_PROVIDER_PACKAGE -> matchedTargets += "sms_provider"
        }

        if (packageLower == "android" || processLower == "android" || processLower == "system") {
            candidateReasons += "system_server_candidate"
        }
        if (containsAnyToken(packageLower, PHONE_TOKENS) || containsAnyToken(processLower, PHONE_TOKENS)) {
            candidateReasons += "phone_token"
        }
        if (containsAnyToken(packageLower, TELEPHONY_TOKENS) || containsAnyToken(processLower, TELEPHONY_TOKENS)) {
            candidateReasons += "telephony_token"
        }
        if (containsAnyToken(packageLower, SMS_TOKENS) || containsAnyToken(processLower, SMS_TOKENS)) {
            candidateReasons += "sms_token"
        }
        if (containsAnyToken(packageLower, MMS_TOKENS) || containsAnyToken(processLower, MMS_TOKENS)) {
            candidateReasons += "mms_token"
        }
        if (containsAnyToken(packageLower, PROVIDER_TOKENS) || containsAnyToken(processLower, PROVIDER_TOKENS)) {
            candidateReasons += "provider_token"
        }

        return ProbeResult(
            candidateReasons = candidateReasons.toList(),
            matchedTargets = matchedTargets.toList(),
        )
    }

    fun logPackageReadyProbeIfVerbose(loadParam: LoadParam) {
        val pluginContext = resolveModuleContext() ?: return
        if (!isVerboseEnabled(pluginContext)) return
        val processName = loadParam.processName.ifBlank { loadParam.packageName }
        val probe = describePackageProbe(loadParam.packageName, processName)
        if (!probe.isCandidate) return
        XLog.w(
            "Diag package ready: pkg=%s process=%s matchedTargets=%s candidateReasons=%s missReason=%s loader=%s",
            loadParam.packageName,
            processName,
            probe.matchedTargets.ifEmpty { listOf("<none>") }.joinToString(","),
            probe.candidateReasons.ifEmpty { listOf("<none>") }.joinToString(","),
            probe.missReason ?: "<none>",
            Integer.toHexString(System.identityHashCode(loadParam.classLoader)),
        )
    }

    fun logTargetProcessHitIfVerbose(
        hookName: String,
        loadParam: LoadParam,
        targetPackage: String,
    ) {
        val pluginContext = resolveModuleContext() ?: return
        if (!isVerboseEnabled(pluginContext)) return
        XLog.w(
            "Diag target process hit: hook=%s pkg=%s process=%s expectedPkg=%s loader=%s",
            hookName,
            loadParam.packageName,
            loadParam.processName.ifBlank { loadParam.packageName },
            targetPackage,
            Integer.toHexString(System.identityHashCode(loadParam.classLoader)),
        )
    }

    fun logTargetMissIfVerbose(
        hookName: String,
        loadParam: LoadParam,
        reason: String,
        detail: String? = null,
    ) {
        val pluginContext = resolveModuleContext() ?: return
        if (!isVerboseEnabled(pluginContext)) return
        XLog.w(
            "Diag target miss: hook=%s pkg=%s process=%s reason=%s detail=%s",
            hookName,
            loadParam.packageName,
            loadParam.processName.ifBlank { loadParam.packageName },
            reason,
            detail ?: "<none>",
        )
    }

    private fun isVerboseEnabled(pluginContext: Context): Boolean {
        return runCatching { XpPrefs.isVerboseLogMode(pluginContext) }.getOrDefault(false)
    }

    private fun resolveModuleContext(): Context? {
        val baseContext = resolveAnyProcessContext() ?: return null
        val appContext = baseContext.applicationContext ?: baseContext
        if (appContext.packageName == BuildConfig.APPLICATION_ID) {
            return appContext
        }
        return runCatching {
            appContext.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull()
    }

    private fun resolveAnyProcessContext(): Context? {
        resolveCurrentApplication()?.let { return it }
        return resolveSystemContext()
    }

    private fun resolveCurrentApplication(): Context? = runCatching {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val currentApplication = activityThreadClass.getDeclaredMethod("currentApplication")
        currentApplication.isAccessible = true
        currentApplication.invoke(null) as? Context
    }.getOrNull()

    private fun resolveSystemContext(): Context? = runCatching {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread")
        currentActivityThread.isAccessible = true
        val thread = currentActivityThread.invoke(null) ?: return null
        val getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext")
        getSystemContext.isAccessible = true
        getSystemContext.invoke(thread) as? Context
    }.getOrNull()

    private fun containsAnyToken(value: String, tokens: List<String>): Boolean {
        return tokens.any(value::contains)
    }

    private const val ANDROID_PHONE_PACKAGE = "com.android.phone"
    private const val TELEPHONY_PROVIDER_PACKAGE = "com.android.providers.telephony"
    private val PHONE_TOKENS = listOf("phone", "dialer")
    private val TELEPHONY_TOKENS = listOf("telephony", "telecom")
    private val SMS_TOKENS = listOf("sms", "message")
    private val MMS_TOKENS = listOf("mms")
    private val PROVIDER_TOKENS = listOf("provider")
}
