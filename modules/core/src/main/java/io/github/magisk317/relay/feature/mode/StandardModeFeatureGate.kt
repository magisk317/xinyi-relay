package io.github.magisk317.relay.feature.mode

/**
 * Defines which features are available in each [WorkMode].
 *
 * - **Enhanced** (Xposed active): all features enabled.
 * - **Standard** (no Xposed, permissions granted): only standard Android API features.
 * - **Inactive**: nothing works.
 *
 * UI screens should consult [isAvailable] to gray-out Xposed-only toggles
 * and show a brief reason tooltip when the user taps a disabled item.
 */
object StandardModeFeatureGate {

    /**
     * Features that require Xposed hook framework to function.
     * When [WorkMode.Standard] is active, these are disabled.
     */
    enum class Feature(val id: String) {
        /** Hook-based SMS interception (SmsHandlerHook / SmsProviderHook) */
        SMS_HOOK_INTERCEPT("sms_hook_intercept"),

        /** Hook-based MMS interception (MmsMessagesHook) */
        MMS_HOOK_INTERCEPT("mms_hook_intercept"),

        /** Block incoming SMS from blacklisted numbers at system level */
        SMS_BLACKLIST_BLOCK("sms_blacklist_block"),

        /** Delete blocked SMS from system database */
        SMS_BLACKLIST_DELETE("sms_blacklist_delete"),

        /** Automatically copy verification code to clipboard on notification */
        COPY_CODE_TO_CLIPBOARD("copy_code_to_clipboard"),

        /** Delete SMS after verification code is auto-filled */
        DELETE_SMS_ON_CODE_INPUT("delete_sms_on_code_input"),

        /** Send SMS triggered by notification events */
        SEND_SMS_ON_NOTIFICATION("send_sms_on_notification"),

        /** Observe SMS inbox changes via Xposed hook */
        SMS_INBOX_OBSERVER("sms_inbox_observer"),

        /** Call interception via Xposed hook (InboundSmsHandler fallback) */
        CALL_HOOK_INTERCEPT("call_hook_intercept"),

        /** Shizuku-based MMS PDU processing via ContentProvider */
        MMS_SHIZUKU_INTERCEPT("mms_shizuku_intercept"),
    }

    /**
     * Returns true if the given [feature] is available in the current [mode].
     */
    fun isAvailable(feature: Feature, mode: WorkMode): Boolean {
        return when (mode) {
            WorkMode.Enhanced -> true
            WorkMode.Standard -> feature !in XPOSED_ONLY_FEATURES
            WorkMode.Inactive -> false
        }
    }

    /**
     * Returns true if ALL given [features] are available in [mode].
     */
    fun allAvailable(mode: WorkMode, vararg features: Feature): Boolean {
        return features.all { isAvailable(it, mode) }
    }

    /**
     * Returns a user-facing reason resource key for why a feature is disabled.
     * Returns null if the feature is available.
     */
    fun disabledReason(feature: Feature, mode: WorkMode): String? {
        if (isAvailable(feature, mode)) return null
        return when (mode) {
            WorkMode.Standard -> "requires_xposed"
            WorkMode.Inactive -> "inactive"
            else -> null
        }
    }

    private val XPOSED_ONLY_FEATURES = setOf(
        Feature.SMS_HOOK_INTERCEPT,
        Feature.MMS_HOOK_INTERCEPT,
        Feature.SMS_BLACKLIST_BLOCK,
        Feature.SMS_BLACKLIST_DELETE,
        Feature.COPY_CODE_TO_CLIPBOARD,
        Feature.DELETE_SMS_ON_CODE_INPUT,
        Feature.SEND_SMS_ON_NOTIFICATION,
        Feature.SMS_INBOX_OBSERVER,
        Feature.CALL_HOOK_INTERCEPT,
    )
}
