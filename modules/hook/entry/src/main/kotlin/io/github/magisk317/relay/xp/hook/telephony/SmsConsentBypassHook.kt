package io.github.magisk317.relay.xp.hook.telephony

import android.app.PendingIntent
import android.content.Context
import io.github.magisk317.relay.contract.constant.RelayPrefConst
import io.github.magisk317.relay.hookentry.BuildConfig
import io.github.magisk317.relay.xp.hook.PhoneHookTargetPackages
import io.github.magisk317.relay.xp.hook.helper.XinyiHookHostCapabilities
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.capability.prefs.CorePrefs
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.StageEventEmitter
import io.github.magisk317.xposed.logging.XLog
import java.lang.reflect.Method

/**
 * Bypasses the MIUI/HyperOS SMS short-code confirmation dialog for xinyi's own outgoing SMS.
 *
 * On HyperOS the telephony stack funnels every app-originated SMS through
 * `MiuiSmsControllerImpl.startConsentUiIfNeeded(...)`. For an untrusted caller (anything that is
 * not a system app, platform-signed, or the default SMS app) it returns `true` and launches
 * `SendSmsVerificationActivity`, which blocks the send until the user taps Allow. That dialog
 * breaks unattended relay sending: xinyi's `sentIntent` only fires after the user intervenes, and
 * on Cancel it never fires at all (the 60s [io.github.magisk317.relay.forward.sender.SmsSendBackend]
 * timeout).
 *
 * Returning `false` from `startConsentUiIfNeeded` makes the caller (`SmsController`) proceed to send
 * the SMS directly through `SmsManager.sendTextMessageInternal`, so xinyi's own `sentIntent` fires
 * normally. The hook is scoped to xinyi's own package, so other apps keep MIUI's consent
 * protection intact.
 */
class SmsConsentBypassHook : BaseHook() {
    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != PhoneHookTargetPackages.ANDROID_PHONE) return
        installBypass(param)
    }

    private fun installBypass(param: LoadParam) {
        val targetClass = runCatching {
            HookHelpers.findClass(SmsConsentBypassConst.TARGET_CLASS, param.classLoader)
        }.getOrNull() ?: run {
            XLog.w(
                message = "SmsConsentBypassHook: class not found: %s (non-MIUI ROM?)",
                SmsConsentBypassConst.TARGET_CLASS,
            )
            recordInstall(param, status = "class_missing", detail = SmsConsentBypassConst.TARGET_CLASS)
            return
        }
        val exactMethod = findConsentMethod(targetClass)
        if (exactMethod == null) {
            XLog.e(
                message = "SmsConsentBypassHook: exact method not found: %s%s in %s",
                SmsConsentBypassConst.TARGET_METHOD,
                SmsConsentBypassConst.TARGET_SIGNATURE,
                SmsConsentBypassConst.TARGET_CLASS,
            )
            recordInstall(
                param,
                status = "method_missing",
                detail = SmsConsentBypassConst.TARGET_METHOD + SmsConsentBypassConst.TARGET_SIGNATURE,
            )
            return
        }
        val hookError = runCatching {
            HookEnv.api.hookMethod(exactMethod, ConsentBypassMethodHook())
        }.exceptionOrNull()
        if (hookError != null) {
            XLog.e(
                message = "SmsConsentBypassHook: hook install failed: %s",
                hookError.javaClass.simpleName,
            )
            recordInstall(param, status = "hook_error", detail = hookError.javaClass.simpleName)
            return
        }
        XLog.i(
            message = "SmsConsentBypassHook installed on %s.%s%s",
            SmsConsentBypassConst.TARGET_CLASS,
            SmsConsentBypassConst.TARGET_METHOD,
            SmsConsentBypassConst.TARGET_SIGNATURE,
        )
        recordInstall(
            param,
            status = "installed",
            detail = SmsConsentBypassConst.TARGET_METHOD + SmsConsentBypassConst.TARGET_SIGNATURE,
        )
    }

    private inner class ConsentBypassMethodHook : MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val enabled = runCatching {
                CorePrefs.getBoolean(
                    RelayPrefConst.KEY_SMS_SHORT_CODE_CONFIRMATION_BYPASS,
                    SmsConsentBypassConst.DEFAULT_ENABLED,
                )
            }.getOrElse { SmsConsentBypassConst.DEFAULT_ENABLED }
            val callingPackage = param.args
                .getOrNull(SmsConsentBypassConst.CALLING_PACKAGE_ARG_INDEX) as? String
            if (!shouldBypassConsent(enabled, callingPackage, BuildConfig.APPLICATION_ID)) return

            param.result = false
            XLog.i(message = "SmsConsentBypassHook: bypassed consent for pkg=%s", callingPackage)
            emitBypass()
        }
    }

    private fun recordInstall(param: LoadParam, status: String, detail: String) {
        XinyiHookHostCapabilities.recordHookInstall(
            context = null,
            hook = SmsConsentBypassConst.HOOK_NAME,
            status = status,
            packageName = param.packageName,
            processName = param.processName,
            detail = detail,
            verboseLogging = false,
        )
    }

    private fun emitBypass() {
        StageEventEmitter.emit(
            name = "sms.consent_bypass",
            result = "ok",
            stage = "bypass",
            reason = "consent_skipped",
            source = "xinyi-relay",
            statusOk = true,
        )
        MagiskOtel.event(
            name = "sms_consent_bypass",
            attributes = mapOf("result" to "ok", "source" to "xinyi-relay"),
            statusOk = true,
        )
    }
}

/**
 * Resolve only the OS4 method archived for pudding. A same-name overload must never be selected.
 */
internal fun findConsentMethod(targetClass: Class<*>): Method? = runCatching {
    targetClass.getDeclaredMethod(
        SmsConsentBypassConst.TARGET_METHOD,
        Context::class.java,
        String::class.java,
        Int::class.javaPrimitiveType,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        PendingIntent::class.java,
        PendingIntent::class.java,
        Boolean::class.javaPrimitiveType,
        Long::class.javaPrimitiveType,
    )
}.getOrNull()

/** Bypass only for the explicit system setting and xinyi's own Binder calling package. */
internal fun shouldBypassConsent(
    enabled: Boolean,
    callingPackage: String?,
    ownPackage: String,
): Boolean = enabled && callingPackage == ownPackage
