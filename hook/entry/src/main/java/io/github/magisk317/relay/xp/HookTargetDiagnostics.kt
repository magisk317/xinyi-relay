package io.github.magisk317.relay.xp

import android.content.Context
import android.util.Log
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.xpbridge.XpPrefs
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.ConcurrentHashMap

internal object HookTargetDiagnostics {
    data class InboundSmsClassProbe(
        val handlerClassFound: Boolean,
        val handlerDispatchIntentFound: Boolean,
        val dispatchersControllerClassFound: Boolean,
        val dispatchersControllerDispatchFound: Boolean,
    )

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

    fun logInboundSmsClassProbeAtInfo(loadParam: LoadParam) {
        val processName = loadParam.processName.ifBlank { loadParam.packageName }
        if (!shouldLogInboundSmsClassProbe(loadParam.packageName, processName)) return
        val probeKey = buildProbeKey(loadParam.packageName, processName)
        if (!loggedInboundSmsClassProbes.add(probeKey)) return

        val classProbe = probeInboundSmsClasses(loadParam.classLoader)
        val packageProbe = describePackageProbe(loadParam.packageName, processName)
        val mismatch = classProbe.handlerClassFound && loadParam.packageName != ANDROID_PHONE_PACKAGE
        XLog.i(
            "Diag inbound probe: pkg=%s process=%s matchedTargets=%s candidateReasons=%s " +
                "handlerClass=%s handlerDispatch=%s controllerClass=%s controllerDispatch=%s " +
                "expectedHookPkg=%s mismatch=%s",
            loadParam.packageName,
            processName,
            packageProbe.matchedTargets.ifEmpty { listOf("<none>") }.joinToString(","),
            packageProbe.candidateReasons.ifEmpty { listOf("<none>") }.joinToString(","),
            classProbe.handlerClassFound,
            classProbe.handlerDispatchIntentFound,
            classProbe.dispatchersControllerClassFound,
            classProbe.dispatchersControllerDispatchFound,
            ANDROID_PHONE_PACKAGE,
            mismatch,
        )
    }

    fun logInboundSmsRuntimeHitAtInfo(
        source: String,
        packageName: String,
        processName: String,
        detail: String? = null,
    ) {
        val hitKey = "$source|$packageName|$processName"
        if (!loggedInboundSmsRuntimeHits.add(hitKey)) return
        XLog.i(
            "Diag inbound hit: source=%s pkg=%s process=%s detail=%s",
            source,
            packageName,
            processName,
            detail ?: "<none>",
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

    internal fun shouldLogInboundSmsClassProbe(
        packageName: String,
        processName: String,
    ): Boolean {
        if (packageName == ANDROID_PHONE_PACKAGE || packageName == "android" || packageName == "system") {
            return true
        }
        val probe = describePackageProbe(packageName, processName)
        return probe.matchedTargets.contains("sms_handler,sms_forward") ||
            probe.candidateReasons.contains("system_server_candidate")
    }

    internal fun probeInboundSmsClasses(classLoader: ClassLoader?): InboundSmsClassProbe {
        val handlerClass = findClass(classLoader, INBOUND_SMS_HANDLER_CLASS)
        val controllerClass = findClass(classLoader, SMS_DISPATCHERS_CONTROLLER_CLASS)
        return InboundSmsClassProbe(
            handlerClassFound = handlerClass != null,
            handlerDispatchIntentFound = handlerClass?.declaredMethods?.any { it.name == "dispatchIntent" } == true,
            dispatchersControllerClassFound = controllerClass != null,
            dispatchersControllerDispatchFound = controllerClass?.declaredMethods?.any {
                it.name == "dispatchSmsDeliveryIntent"
            } == true,
        )
    }

    private fun buildProbeKey(packageName: String, processName: String): String {
        return "$packageName|$processName"
    }

    private fun findClass(classLoader: ClassLoader?, name: String): Class<*>? {
        if (classLoader == null) return null
        return runCatching { Class.forName(name, false, classLoader) }.getOrNull()
    }

    private const val ANDROID_PHONE_PACKAGE = "com.android.phone"
    private const val TELEPHONY_PROVIDER_PACKAGE = "com.android.providers.telephony"
    private const val INBOUND_SMS_HANDLER_CLASS = "com.android.internal.telephony.InboundSmsHandler"
    private const val SMS_DISPATCHERS_CONTROLLER_CLASS = "com.android.internal.telephony.SmsDispatchersController"
    private val PHONE_TOKENS = listOf("phone", "dialer")
    private val TELEPHONY_TOKENS = listOf("telephony", "telecom")
    private val SMS_TOKENS = listOf("sms", "message")
    private val MMS_TOKENS = listOf("mms")
    private val PROVIDER_TOKENS = listOf("provider")
    private val loggedInboundSmsClassProbes = ConcurrentHashMap.newKeySet<String>()
    private val loggedInboundSmsRuntimeHits = ConcurrentHashMap.newKeySet<String>()
}
