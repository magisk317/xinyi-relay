package io.github.magisk317.relay.feature.mode

import io.github.magisk317.relay.feature.mode.FeatureGate.Feature.BLOCK_SMS
import io.github.magisk317.relay.feature.mode.FeatureGate.Feature.KEEPALIVE_OOM_ADJ
import io.github.magisk317.relay.feature.mode.FeatureGate.Feature.ROOT_DB_CATCHUP
import io.github.magisk317.relay.feature.mode.FeatureGate.Feature.SMS_HOOK_INTERCEPT
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeatureGateTest {

    @Test
    fun `Hook-backed features need a live Xposed runtime`() {
        listOf(SMS_HOOK_INTERCEPT, BLOCK_SMS, KEEPALIVE_OOM_ADJ).forEach { feature ->
            assertTrue(
                FeatureGate.isAvailable(feature, xposedActive = true),
                "$feature should be available when Xposed is active",
            )
            assertFalse(
                FeatureGate.isAvailable(feature, xposedActive = false),
                "$feature should be unavailable when Xposed is inactive",
            )
        }
    }

    @Test
    fun `Root-only features need root access on top of the Xposed runtime`() {
        assertFalse(FeatureGate.isAvailable(ROOT_DB_CATCHUP, xposedActive = true, hasRootAccess = false))
        assertTrue(FeatureGate.isAvailable(ROOT_DB_CATCHUP, xposedActive = true, hasRootAccess = true))
    }

    @Test
    fun `An inactive Xposed runtime disables every gated feature`() {
        assertFalse(FeatureGate.isAvailable(SMS_HOOK_INTERCEPT, xposedActive = false, hasRootAccess = true))
        assertFalse(FeatureGate.isAvailable(ROOT_DB_CATCHUP, xposedActive = false, hasRootAccess = true))
    }

    @Test
    fun `allAvailable requires every feature to be available`() {
        val keepAlive = arrayOf(FeatureGate.Feature.KEEPALIVE_ANTI_KILL, KEEPALIVE_OOM_ADJ)
        assertTrue(FeatureGate.allAvailable(xposedActive = true, *keepAlive))
        assertFalse(FeatureGate.allAvailable(xposedActive = false, *keepAlive))
    }
}
