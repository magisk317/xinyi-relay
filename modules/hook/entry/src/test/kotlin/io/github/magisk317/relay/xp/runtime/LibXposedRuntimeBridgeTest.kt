package io.github.magisk317.relay.xp.runtime

import android.content.SharedPreferences
import io.github.magisk317.relay.contract.constant.RelayPrefConst
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class LibXposedRuntimeBridgeTest {
    @Test
    fun reflectiveRemoteProviderIsResolvedForEachRead() {
        val handle = TestRuntimeHandle()
        val source = LibXposedRuntimeBridge(handle).remotePrefsSource(RelayPrefConst.REMOTE_PREFS_GROUP)

        handle.remote = ReflectiveRemotePrefs(
            mapOf(
                "enabled" to true,
                "label" to "before",
            ),
        )
        assertEquals(PrefRead.Hit(true, "remote_libxposed"), source.readBoolean("enabled", false))
        assertEquals(PrefRead.Hit("before", "remote_libxposed"), source.readString("label", "fallback"))

        handle.remote = ReflectiveRemotePrefs(
            mapOf(
                "enabled" to false,
                "label" to "",
            ),
        )
        assertEquals(PrefRead.Hit(false, "remote_libxposed"), source.readBoolean("enabled", true))
        assertEquals(PrefRead.Hit("", "remote_libxposed"), source.readString("label", "fallback"))

        handle.remote = ReflectiveRemotePrefs(emptyMap())
        assertSame(PrefRead.Miss, source.readBoolean("enabled", true))

        handle.remote = null
        assertSame(PrefRead.Unavailable, source.readBoolean("enabled", true))
    }

    @Test
    fun sharedPreferencesRemoteProviderIsResolvedForEachRead() {
        val first = mockk<SharedPreferences>()
        every { first.contains("enabled") } returns true
        every { first.getBoolean("enabled", false) } returns true
        every { first.all } returns mapOf("enabled" to true)
        val second = mockk<SharedPreferences>()
        every { second.contains("enabled") } returns true
        every { second.getBoolean("enabled", true) } returns false
        every { second.all } returns mapOf("enabled" to false)
        val handle = TestRuntimeHandle()
        val source = LibXposedRuntimeBridge(handle).remotePrefsSource(RelayPrefConst.REMOTE_PREFS_GROUP)

        handle.remote = first
        assertEquals(PrefRead.Hit(true, "remote_libxposed"), source.readBoolean("enabled", false))
        handle.remote = second
        assertEquals(PrefRead.Hit(false, "remote_libxposed"), source.readBoolean("enabled", true))
        handle.remote = null
        assertSame(PrefRead.Unavailable, source.readBoolean("enabled", true))
    }

    private class TestRuntimeHandle {
        var remote: Any? = null

        fun getRemotePreferences(group: String): Any? = remote
    }

    private class ReflectiveRemotePrefs(
        private val values: Map<String, Any?>,
    ) {
        fun getAll(): Map<String, Any?> = values
    }
}
