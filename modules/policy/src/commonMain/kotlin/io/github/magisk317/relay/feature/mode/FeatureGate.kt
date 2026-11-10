package io.github.magisk317.relay.feature.mode

/**
 * Defines which gated features are available.
 *
 * - Root DB catch-up features need root access only. They do not depend on the
 *   Xposed runtime or the non-root runtime.
 * - Force-stop recovery features need the Xposed runtime and root access.
 * - Every other feature needs the Xposed runtime, or the non-root runtime when
 *   the feature supports non-root mode.
 *
 * UI screens should consult [isAvailable] to gray-out gated toggles and append
 * a short reason hint when the user taps a disabled item.
 */
object FeatureGate {

    /** Gated feature ids carried across the settings and UI layer. */
    enum class Feature(val id: String) {
        /** Block incoming SMS from blacklisted numbers at system level */
        SMS_BLACKLIST_BLOCK("sms_blacklist_block"),

        /** Delete blocked SMS from system database */
        SMS_BLACKLIST_DELETE("sms_blacklist_delete"),

        /** Automatically copy verification code to clipboard on notification */
        COPY_CODE_TO_CLIPBOARD("copy_code_to_clipboard"),

        /** Keep-alive: adjust OOM adj score via Xposed hook */
        KEEPALIVE_OOM_ADJ("keepalive_oom_adj"),

        /** Keep-alive: prevent process kill via Xposed hook */
        KEEPALIVE_ANTI_KILL("keepalive_anti_kill"),

        /** Keep-alive: bypass standby bucket restrictions via Xposed hook */
        KEEPALIVE_STANDBY_BYPASS("keepalive_standby_bypass"),

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
     * Returns true if the given [feature] can run. Root DB catch-up features need
     * [hasRootAccess] only. Force-stop recovery features need [xposedActive] and
     * [hasRootAccess]. Every other feature needs [xposedActive], or [nonRootActive]
     * when the feature is in the non-root set.
     */
    fun isAvailable(
        feature: Feature,
        xposedActive: Boolean,
        hasRootAccess: Boolean = false,
        nonRootActive: Boolean = false,
    ): Boolean = when (feature) {
        in ROOT_DB_FEATURES -> hasRootAccess
        in FORCE_STOP_FEATURES -> xposedActive && hasRootAccess
        else -> xposedActive || (nonRootActive && feature in NON_ROOT_FEATURES)
    }

    /**
     * Returns true if ALL given [features] are available. Callers must pass
     * [nonRootActive] explicitly, using the same value they give to [isAvailable].
     */
    fun allAvailable(
        xposedActive: Boolean,
        nonRootActive: Boolean,
        vararg features: Feature,
    ): Boolean {
        return features.all { isAvailable(it, xposedActive, nonRootActive = nonRootActive) }
    }

    /**
     * Returns true if ALL given [features] are available with root and non-root state.
     */
    fun allAvailable(
        xposedActive: Boolean,
        hasRootAccess: Boolean,
        nonRootActive: Boolean,
        vararg features: Feature,
    ): Boolean {
        return features.all { isAvailable(it, xposedActive, hasRootAccess, nonRootActive) }
    }

    /** Root DB catch-up features. They need root access (su) only. */
    private val ROOT_DB_FEATURES = setOf(
        Feature.ROOT_DB_CATCHUP,
        Feature.ROOT_DB_CATCHUP_WRITEBACK,
    )

    /** Force-stop recovery features. They need the Xposed runtime and root access (su). */
    private val FORCE_STOP_FEATURES = setOf(
        Feature.FORCE_STOP_RECOVERY,
        Feature.FORCE_STOP_RECOVERY_RELAUNCH_ONCE,
    )

    /**
     * Features the notification-listener runtime can serve without hooks:
     * the payload already carries the parsed code and the relay pipeline owns
     * the copy action.
     */
    private val NON_ROOT_FEATURES = setOf(
        Feature.COPY_CODE_TO_CLIPBOARD,
    )
}
