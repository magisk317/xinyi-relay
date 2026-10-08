package io.github.magisk317.relay.feature.mode

/**
 * Defines which gated features are available.
 *
 * - Xposed runtime inactive: there is no runtime path at all, so every gated
 *   feature is off.
 * - Xposed runtime active: every gated feature is available except the
 *   root-backed ones, which need the root capability separately.
 *
 * UI screens should consult [isAvailable] to gray-out gated toggles and append
 * a short reason hint when the user taps a disabled item.
 */
object FeatureGate {

    /** Gated feature ids carried across the settings and UI layer. */
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
     * Returns true if the given [feature] can run: the Xposed runtime must be
     * live, and root-backed features additionally need [hasRootAccess].
     */
    fun isAvailable(
        feature: Feature,
        xposedActive: Boolean,
        hasRootAccess: Boolean = false,
    ): Boolean = when {
        !xposedActive -> false
        feature in ROOT_ONLY_FEATURES && !hasRootAccess -> false
        else -> true
    }

    /**
     * Returns true if ALL given [features] are available.
     */
    fun allAvailable(xposedActive: Boolean, vararg features: Feature): Boolean {
        return features.all { isAvailable(it, xposedActive) }
    }

    /**
     * Returns true if ALL given [features] are available with root state.
     */
    fun allAvailable(xposedActive: Boolean, hasRootAccess: Boolean, vararg features: Feature): Boolean {
        return features.all { isAvailable(it, xposedActive, hasRootAccess) }
    }

    /** Features that require Root access (su) to function. */
    private val ROOT_ONLY_FEATURES = setOf(
        Feature.ROOT_DB_CATCHUP,
        Feature.ROOT_DB_CATCHUP_WRITEBACK,
        Feature.FORCE_STOP_RECOVERY,
        Feature.FORCE_STOP_RECOVERY_RELAUNCH_ONCE,
    )
}
