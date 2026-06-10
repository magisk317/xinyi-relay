package io.github.magisk317.relay.xp.hook

internal object SmsHookDispatchGate {
    enum class BlockReason {
        MODULE_DISABLED,
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
    ): Decision {
        if (!moduleEnabled) {
            return Decision(
                blocked = true,
                reason = BlockReason.MODULE_DISABLED,
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
