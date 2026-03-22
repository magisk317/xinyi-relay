package io.github.magisk317.relay.xp.hook.code

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsInboxObserverDecisionTest {

    @Test
    fun evaluate_skipsWhenConflictSuppressed() {
        val decision = SmsInboxObserverDecision.evaluate(
            moduleEnabled = true,
            suppressedByRelay = true,
            duplicated = false,
            plan = observedPlan(),
        )

        assertEquals(SmsInboxObserverDecision.SkipReason.CONFLICT_SUPPRESSED, decision.skipReason)
        assertFalse(decision.shouldProceed)
        assertFalse(decision.autoInputEnabled)
    }

    @Test
    fun evaluate_skipsWhenModuleDisabled() {
        val decision = SmsInboxObserverDecision.evaluate(
            moduleEnabled = false,
            suppressedByRelay = false,
            duplicated = false,
            plan = observedPlan(),
        )

        assertEquals(SmsInboxObserverDecision.SkipReason.MODULE_DISABLED, decision.skipReason)
        assertFalse(decision.shouldProceed)
        assertFalse(decision.autoInputEnabled)
    }

    @Test
    fun evaluate_skipsWhenObservedSmsDuplicated() {
        val decision = SmsInboxObserverDecision.evaluate(
            moduleEnabled = true,
            suppressedByRelay = false,
            duplicated = true,
            plan = observedPlan(),
        )

        assertEquals(SmsInboxObserverDecision.SkipReason.DUPLICATED, decision.skipReason)
        assertFalse(decision.shouldProceed)
        assertFalse(decision.autoInputEnabled)
    }

    @Test
    fun evaluate_marksRecordSkipReasonFromPlan() {
        val decision = SmsInboxObserverDecision.evaluate(
            moduleEnabled = true,
            suppressedByRelay = false,
            duplicated = false,
            plan = observedPlan(
                autoInputEnabled = false,
                shouldRecord = false,
                deduplicateSmsEnabled = true,
            ),
        )

        assertNull(decision.skipReason)
        assertTrue(decision.shouldProceed)
        assertFalse(decision.autoInputEnabled)
        assertEquals(SmsInboxObserverDecision.RecordSkipReason.DEDUP_ENABLED, decision.recordSkipReason)
    }

    @Test
    fun evaluate_allowsDispatchWhenObserverPathHealthy() {
        val decision = SmsInboxObserverDecision.evaluate(
            moduleEnabled = true,
            suppressedByRelay = false,
            duplicated = false,
            plan = observedPlan(
                autoInputEnabled = true,
                shouldRecord = true,
                deduplicateSmsEnabled = false,
            ),
        )

        assertNull(decision.skipReason)
        assertTrue(decision.shouldProceed)
        assertTrue(decision.autoInputEnabled)
        assertNull(decision.recordSkipReason)
    }

    private fun observedPlan(
        autoInputEnabled: Boolean = true,
        shouldRecord: Boolean = true,
        deduplicateSmsEnabled: Boolean = false,
    ): SmsCodePostParseCoordinator.ObservedSmsPlan {
        return SmsCodePostParseCoordinator.ObservedSmsPlan(
            deduplicateSmsEnabled = deduplicateSmsEnabled,
            autoInputEnabled = autoInputEnabled,
            shouldRecord = shouldRecord,
        )
    }
}
