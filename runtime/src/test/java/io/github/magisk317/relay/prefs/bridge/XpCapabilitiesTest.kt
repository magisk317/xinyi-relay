package io.github.magisk317.relay.prefs.bridge

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XpCapabilitiesTest {

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

    @Test
    fun hasFrameworkProperty_returnsFalseWhenPropertiesUnknown() {
        val capabilities = XpCapabilities(
            frameworkName = "libxposed",
            frameworkVersion = "unknown",
            frameworkProperties = null,
        )

        assertFalse(capabilities.hasFrameworkProperty(XpCapabilities.PROP_CAP_REMOTE))
    }
}
