package io.github.magisk317.relay.feature.mode

import io.github.magisk317.relay.feature.mode.FeatureGate.Feature
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeatureGateTest {
    @Test
    fun `hook features require Xposed even with a connected listener`() {
        for (feature in listOf(Feature.BLOCK_SMS, Feature.SMS_BLACKLIST_BLOCK, Feature.KEEPALIVE_OOM_ADJ)) {
            assertTrue(FeatureGate.isAvailable(feature, true))
            assertFalse(FeatureGate.isAvailable(feature, false, nonRootActive = true))
        }
    }

    @Test
    fun `root catch-up requires root without depending on Xposed or listener`() {
        assertTrue(FeatureGate.isAvailable(Feature.ROOT_DB_CATCHUP, false, hasRootAccess = true))
        assertTrue(FeatureGate.isAvailable(Feature.ROOT_DB_CATCHUP_WRITEBACK, false, hasRootAccess = true))
        assertFalse(FeatureGate.isAvailable(Feature.ROOT_DB_CATCHUP, true, nonRootActive = true))
    }

    @Test
    fun `listener enables code copy but not force-stop recovery`() {
        assertTrue(FeatureGate.isAvailable(Feature.COPY_CODE_TO_CLIPBOARD, false, nonRootActive = true))
        assertFalse(FeatureGate.isAvailable(Feature.FORCE_STOP_RECOVERY, false, true, true))
        assertTrue(FeatureGate.isAvailable(Feature.FORCE_STOP_RECOVERY, true, true, false))
    }
}
