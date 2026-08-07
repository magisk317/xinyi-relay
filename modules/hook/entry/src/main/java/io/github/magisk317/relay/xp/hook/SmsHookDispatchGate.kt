package io.github.magisk317.relay.xp.hook

internal object SmsHookDispatchGate {
    enum class BlockReason {
        MODULE_DISABLED,
        MOBILE_ENTITLEMENT_UNAVAILABLE,
        RELAY_DISABLED,
        CONFLICT_SUPPRESSED,
    }

    data class Decision(
        val blocked: Boolean,
        val reason: BlockReason? = null,
    )

    fun evaluate(
        moduleEnabled: Boolean,
        relayFeatureRequired: Boolean,
        relayFeaturesEnabled: Boolean,
        suppressedByRelay: Boolean,
        mobileAutomationAllowed: Boolean = true,
    ): Decision {
        if (!moduleEnabled) {
            return Decision(
                blocked = true,
                reason = BlockReason.MODULE_DISABLED,
            )
        }
        if (!mobileAutomationAllowed) {
            return Decision(
                blocked = true,
                reason = BlockReason.MOBILE_ENTITLEMENT_UNAVAILABLE,
            )
        }
        if (relayFeatureRequired && !relayFeaturesEnabled) {
            return Decision(
                blocked = true,
                reason = BlockReason.RELAY_DISABLED,
            )
        }
        if (suppressedByRelay) {
            return Decision(
                blocked = true,
                reason = BlockReason.CONFLICT_SUPPRESSED,
            )
        }
        return Decision(blocked = false)
    }
}
