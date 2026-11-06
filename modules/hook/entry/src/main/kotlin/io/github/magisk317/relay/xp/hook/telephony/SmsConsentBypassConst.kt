package io.github.magisk317.relay.xp.hook.telephony

/**
 * Constants for the SMS consent (short-code verification) bypass hook.
 *
 * @see SmsConsentBypassHook
 */
internal object SmsConsentBypassConst {
    const val DEFAULT_ENABLED = false

    /** Concrete MIUI/HyperOS class that launches the consent activity. */
    const val TARGET_CLASS = "com.android.internal.telephony.MiuiSmsControllerImpl"
    const val TARGET_METHOD = "startConsentUiIfNeeded"
    const val CALLING_PACKAGE_ARG_INDEX = 1
    const val TARGET_SIGNATURE =
        "(Context,String,int,String,String,String,String,PendingIntent,PendingIntent,boolean,long)"

    const val HOOK_NAME = "sms_consent_bypass"
}
