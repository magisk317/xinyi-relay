package io.github.magisk317.relay.xp.hook.code

internal object SmsInboxObserverDecision {
    enum class SkipReason {
        CONFLICT_SUPPRESSED,
        MODULE_DISABLED,
        DUPLICATED,
    }

    enum class RecordSkipReason(val wireValue: String) {
        DEDUP_ENABLED("dedup_enabled"),
        RECORD_DISABLED("record_disabled"),
    }

    data class Decision(
        val skipReason: SkipReason? = null,
        val autoInputEnabled: Boolean,
        val recordSkipReason: RecordSkipReason? = null,
    ) {
        val shouldProceed: Boolean = skipReason == null
    }

    fun evaluate(
        moduleEnabled: Boolean,
        suppressedByRelay: Boolean,
        duplicated: Boolean,
        plan: SmsCodePostParseCoordinator.ObservedSmsPlan,
    ): Decision {
        if (suppressedByRelay) {
            return Decision(
                skipReason = SkipReason.CONFLICT_SUPPRESSED,
                autoInputEnabled = false,
            )
        }
        if (!moduleEnabled) {
            return Decision(
                skipReason = SkipReason.MODULE_DISABLED,
                autoInputEnabled = false,
            )
        }
        if (duplicated) {
            return Decision(
                skipReason = SkipReason.DUPLICATED,
                autoInputEnabled = false,
            )
        }
        return Decision(
            autoInputEnabled = plan.autoInputEnabled,
            recordSkipReason = when {
                plan.shouldRecord -> null
                plan.deduplicateSmsEnabled -> RecordSkipReason.DEDUP_ENABLED
                else -> RecordSkipReason.RECORD_DISABLED
            },
        )
    }
}
