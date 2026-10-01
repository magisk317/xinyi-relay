package io.github.magisk317.relay.contract.prefs

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XpCapabilitiesContractTest {

    @Test
    fun hasFrameworkProperty_returnsTrueWhenBitIsSet() {
        val capabilities = XpCapabilities(
            frameworkName = "libxposed",
            frameworkVersion = "101.0.0",
            frameworkProperties = XpCapabilities.PROP_CAP_SYSTEM or XpCapabilities.PROP_CAP_REMOTE,
        )

        assertTrue(capabilities.hasFrameworkProperty(XpCapabilities.PROP_CAP_SYSTEM))
        assertTrue(capabilities.hasFrameworkProperty(XpCapabilities.PROP_CAP_REMOTE))
        assertFalse(capabilities.hasFrameworkProperty(XpCapabilities.PROP_RT_API_PROTECTION))
    }
}
