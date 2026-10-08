package io.github.magisk317.relay.android.prefs

import io.github.magisk317.relay.contract.prefs.PrefsSource
import io.github.magisk317.relay.contract.prefs.XpCapabilities
import io.github.magisk317.relay.contract.prefs.XpRuntimeBridge
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrefsSourceChainTest {
    @Test
    fun unsupportedRemoteCapabilityDoesNotResolveOrAddLocalSource() {
        val bridge = TestRuntimeBridge(
            capabilities = XpCapabilities(
                frameworkName = "test",
                frameworkVersion = "test",
                supportsRemotePrefs = false,
            ),
        )

        assertTrue(PrefsSourceChain.resolveSources(bridge) { _, _ -> }.isEmpty())
        assertEquals(0, bridge.remoteSourceCalls)
    }

    @Test
    fun supportedRemoteCapabilityKeepsUnavailableRemoteSource() {
        val source = object : PrefsSource {
            override val sourceName: String = "remote_test"

            override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> = PrefRead.Unavailable

            override fun readString(key: String, defaultValue: String): PrefRead<String> = PrefRead.Unavailable

            override fun readInt(key: String, defaultValue: Int): PrefRead<Int> = PrefRead.Unavailable
        }
        val bridge = TestRuntimeBridge(
            capabilities = XpCapabilities(
                frameworkName = "test",
                frameworkVersion = "test",
                supportsRemotePrefs = true,
            ),
            source = source,
        )

        val sources = PrefsSourceChain.resolveSources(bridge) { _, _ -> }

        assertEquals(listOf("remote_test"), sources.map { it.sourceName })
        assertEquals(1, bridge.remoteSourceCalls)
    }

    private class TestRuntimeBridge(
        private val capabilities: XpCapabilities,
        private val source: PrefsSource = object : PrefsSource {
            override val sourceName: String = "remote_test"

            override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> = PrefRead.Unavailable

            override fun readString(key: String, defaultValue: String): PrefRead<String> = PrefRead.Unavailable

            override fun readInt(key: String, defaultValue: Int): PrefRead<Int> = PrefRead.Unavailable
        },
    ) : XpRuntimeBridge {
        var remoteSourceCalls: Int = 0

        override fun capabilities(): XpCapabilities = capabilities

        override fun remotePrefsSource(group: String): PrefsSource {
            remoteSourceCalls++
            return source
        }
    }
}
