package io.github.magisk317.relay.android.prefs

import android.content.Context
import android.os.SystemClock
import io.github.magisk317.relay.android.BuildConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * The hook process evaluates the entitlement gate from this module, so the
 * gate-off distribution has to short-circuit here rather than reach
 * MobileEntitlementGate: play publishes an always-allowed snapshot carrying no
 * token, and the gate answers "missing_token" - denied - for a token-less state
 * under an enforced policy. Each case only runs on the flavor it describes.
 */
class PrefsReaderEntitlementGateTest {

    @BeforeEach
    fun setUpClock() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L
        PrefsReader.invalidateCache()
    }

    @AfterEach
    fun tearDownClock() {
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `gate-off distribution allows automation without consulting the gate`() {
        assumeFalse(BuildConfig.ENABLE_MOBILE_ENTITLEMENT)

        val context = mockk<Context>(relaxed = true)

        assertTrue(PrefsReader.mobileAutomationAllowed(context))
    }

    @Test
    fun `enforced distribution fails closed without a published token`() {
        assumeTrue(BuildConfig.ENABLE_MOBILE_ENTITLEMENT)

        val context = mockk<Context>(relaxed = true)

        assertFalse(PrefsReader.mobileAutomationAllowed(context))
    }
}
