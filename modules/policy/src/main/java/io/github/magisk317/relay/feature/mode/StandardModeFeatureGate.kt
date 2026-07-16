package io.github.magisk317.relay.feature.mode

/**
 * Defines which features are available in each [WorkMode].
 *
 * - **Enhanced** (Xposed active): Xposed and standard features enabled.
 * - **Standard** (no Xposed, permissions granted): only standard Android API features.
 * - **Inactive**: nothing works.
 *
 * Root-backed features are independent from Xposed and must pass the root
 * capability separately.
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

        /** Keep-alive: adjust OOM adj score via Xposed hook */
        KEEPALIVE_OOM_ADJ("keepalive_oom_adj"),

        /** Keep-alive: prevent process kill via Xposed hook */
        KEEPALIVE_ANTI_KILL("keepalive_anti_kill"),

        /** Keep-alive: bypass standby bucket restrictions via Xposed hook */
        KEEPALIVE_STANDBY_BYPASS("keepalive_standby_bypass"),

        /** Keep-alive: bypass Doze mode restrictions via Xposed hook */
        KEEPALIVE_DOZE_BYPASS("keepalive_doze_bypass"),

        /** Block incoming SMS at system level (Xposed hook required) */
        BLOCK_SMS("block_sms"),

        /** Periodically query SMS/call DB through su + sqlite3 */
        ROOT_DB_CATCHUP("root_db_catchup"),

        /** Write Root DB catchup cursor updates through su + sqlite3 */
        ROOT_DB_CATCHUP_WRITEBACK("root_db_catchup_writeback"),

        /** Recover after force-stop by waking the root DB catchup pipeline */
        FORCE_STOP_RECOVERY("force_stop_recovery"),

        /** Relaunch once after force-stop recovery wakeup */
        FORCE_STOP_RECOVERY_RELAUNCH_ONCE("force_stop_recovery_relaunch_once"),
    }

    /**
     * Returns true if the given [feature] is available in the current [mode].
     */
    fun isAvailable(
        feature: Feature,
        mode: WorkMode,
        hasRootAccess: Boolean = false,
    ): Boolean = when {
        mode == WorkMode.Inactive -> false
        feature in XPOSED_ONLY_FEATURES && mode != WorkMode.Enhanced -> false
        feature in ROOT_ONLY_FEATURES && !hasRootAccess -> false
        else -> true
    }

    /**
     * Returns true if ALL given [features] are available in [mode].
     */
    fun allAvailable(mode: WorkMode, vararg features: Feature): Boolean {
        return features.all { isAvailable(it, mode) }
    }

    /**
     * Returns true if ALL given [features] are available in [mode] with root state.
     */
    fun allAvailable(mode: WorkMode, hasRootAccess: Boolean, vararg features: Feature): Boolean {
        return features.all { isAvailable(it, mode, hasRootAccess) }
    }

    /**
     * Returns a user-facing reason resource key for why a feature is disabled.
     * Returns null if the feature is available.
     */
    fun disabledReason(
        feature: Feature,
        mode: WorkMode,
        hasRootAccess: Boolean = false,
    ): String? = when {
        isAvailable(feature, mode, hasRootAccess) -> null
        mode == WorkMode.Inactive -> "inactive"
        feature in ROOT_ONLY_FEATURES && !hasRootAccess -> "requires_root"
        feature in XPOSED_ONLY_FEATURES && mode != WorkMode.Enhanced -> "requires_xposed"
        else -> null
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
        Feature.KEEPALIVE_OOM_ADJ,
        Feature.KEEPALIVE_ANTI_KILL,
        Feature.KEEPALIVE_STANDBY_BYPASS,
        Feature.KEEPALIVE_DOZE_BYPASS,
        Feature.BLOCK_SMS,
    )

    /** Features that require Root access (su) to function. */
    private val ROOT_ONLY_FEATURES = setOf(
        Feature.ROOT_DB_CATCHUP,
        Feature.ROOT_DB_CATCHUP_WRITEBACK,
        Feature.FORCE_STOP_RECOVERY,
        Feature.FORCE_STOP_RECOVERY_RELAUNCH_ONCE,
    )
}
