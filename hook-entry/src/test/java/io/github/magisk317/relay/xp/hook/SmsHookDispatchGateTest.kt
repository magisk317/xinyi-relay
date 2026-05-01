package io.github.magisk317.relay.xp.hook

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsHookDispatchGateTest {

    @Test
    fun evaluate_blocksWhenModuleDisabled() {
        val decision = SmsHookDispatchGate.evaluate(
            moduleEnabled = false,
            relayFeatureRequired = false,
            relayFeaturesEnabled = true,
            suppressedByRelay = false,
        )

        assertTrue(decision.blocked)
        assertEquals(SmsHookDispatchGate.BlockReason.MODULE_DISABLED, decision.reason)
    }

    @Test
    fun evaluate_blocksWhenRelayFeatureRequiredButDisabled() {
        val decision = SmsHookDispatchGate.evaluate(
            moduleEnabled = true,
            relayFeatureRequired = true,
            relayFeaturesEnabled = false,
            suppressedByRelay = false,
        )

        assertTrue(decision.blocked)
        assertEquals(SmsHookDispatchGate.BlockReason.RELAY_DISABLED, decision.reason)
    }

    @Test
    fun evaluate_blocksWhenSuppressedByConflict() {
        val decision = SmsHookDispatchGate.evaluate(
            moduleEnabled = true,
            relayFeatureRequired = false,
            relayFeaturesEnabled = true,
            suppressedByRelay = true,
        )

        assertTrue(decision.blocked)
        assertEquals(SmsHookDispatchGate.BlockReason.CONFLICT_SUPPRESSED, decision.reason)
    }

    @Test
    fun evaluate_allowsHealthyHookDispatch() {
        val decision = SmsHookDispatchGate.evaluate(
            moduleEnabled = true,
            relayFeatureRequired = true,
            relayFeaturesEnabled = true,
            suppressedByRelay = false,
        )

        assertFalse(decision.blocked)
        assertNull(decision.reason)
    }
}
