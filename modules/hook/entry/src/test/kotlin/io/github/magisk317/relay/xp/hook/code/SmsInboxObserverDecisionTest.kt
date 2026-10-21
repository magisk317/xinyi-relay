package io.github.magisk317.relay.xp.hook.code

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.github.magisk317.smscode.verification.SmsInboxObserverDecision
import io.github.magisk317.smscode.verification.SmsDuplicateCheckResult

class SmsInboxObserverDecisionTest {

    @Test
    fun evaluate_skipsWhenConflictSuppressed() {
        val decision = SmsInboxObserverDecision.evaluate(
            moduleEnabled = true,
            suppressedByRelay = true,
            duplicateCheckResult = SmsDuplicateCheckResult.NOT_DUPLICATE,
            autoInputEnabled = true,
            shouldRecord = true,
            deduplicateSmsEnabled = false,
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
            duplicateCheckResult = SmsDuplicateCheckResult.NOT_DUPLICATE,
            autoInputEnabled = true,
            shouldRecord = true,
            deduplicateSmsEnabled = false,
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
            duplicateCheckResult = SmsDuplicateCheckResult.DUPLICATE,
            autoInputEnabled = true,
            shouldRecord = true,
            deduplicateSmsEnabled = true,
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
            duplicateCheckResult = SmsDuplicateCheckResult.NOT_DUPLICATE,
            autoInputEnabled = false,
            shouldRecord = false,
            deduplicateSmsEnabled = true,
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
            duplicateCheckResult = SmsDuplicateCheckResult.NOT_DUPLICATE,
            autoInputEnabled = true,
            shouldRecord = true,
            deduplicateSmsEnabled = false,
        )

        assertNull(decision.skipReason)
        assertTrue(decision.shouldProceed)
        assertTrue(decision.autoInputEnabled)
        assertNull(decision.recordSkipReason)
    }

    @Test
    fun evaluate_failsClosedWhenDuplicateCheckIsUnavailable() {
        val decision = SmsInboxObserverDecision.evaluate(
            moduleEnabled = true,
            suppressedByRelay = false,
            duplicateCheckResult = SmsDuplicateCheckResult.UNAVAILABLE,
            autoInputEnabled = true,
            shouldRecord = true,
            deduplicateSmsEnabled = true,
        )

        assertEquals(SmsInboxObserverDecision.SkipReason.DUPLICATE_CHECK_UNAVAILABLE, decision.skipReason)
        assertFalse(decision.shouldProceed)
        assertFalse(decision.autoInputEnabled)
    }
}
