package io.github.magisk317.relay.android.prefs

import android.content.Context
import android.os.SystemClock
import io.github.magisk317.relay.contract.prefs.PrefsSource
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import io.mockk.mockk
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrefsReaderRemotePreferencesTest {
    @org.junit.jupiter.api.BeforeEach
    fun setUpClock() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L
        PrefsReader.invalidateCache()
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDownClock() {
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun unavailableRemoteValueDoesNotReusePreviousResolvedValue() {
        val source = MutableSource()
        val context = mockk<Context>(relaxed = true)

        source.booleanRead = PrefRead.Hit(true, "remote_test")
        val first = PrefsReader.resolveBooleanWithSourcesForTest(context, "enabled", false, listOf(source))

        source.booleanRead = PrefRead.Unavailable
        val second = PrefsReader.resolveBooleanWithSourcesForTest(context, "enabled", false, listOf(source))

        assertTrue(first.value)
        assertFalse(second.value)
        assertEquals("default", second.source)
    }

    @Test
    fun missingAndExplicitValuesKeepTheirDistinctSourceSemantics() {
        val source = MutableSource()
        val context = mockk<Context>(relaxed = true)

        source.booleanRead = PrefRead.Miss
        val missingBoolean = PrefsReader.resolveBooleanWithSourcesForTest(context, "enabled", true, listOf(source))
        source.booleanRead = PrefRead.Hit(false, "remote_test")
        val explicitFalse = PrefsReader.resolveBooleanWithSourcesForTest(context, "enabled", true, listOf(source))

        source.stringRead = PrefRead.Miss
        val missingString = PrefsReader.resolveStringWithSourcesForTest(context, "label", "fallback", listOf(source))
        source.stringRead = PrefRead.Hit("", "remote_test")
        val explicitEmpty = PrefsReader.resolveStringWithSourcesForTest(context, "label", "fallback", listOf(source))

        assertEquals(true, missingBoolean.value)
        assertEquals("default", missingBoolean.source)
        assertFalse(explicitFalse.value)
        assertEquals("remote_test", explicitFalse.source)
        assertEquals("fallback", missingString.value)
        assertEquals("default", missingString.source)
        assertEquals("", explicitEmpty.value)
        assertEquals("remote_test", explicitEmpty.source)
    }

    @Test
    fun remoteHitRefreshesWhenTheProviderChanges() {
        val source = MutableSource()
        val context = mockk<Context>(relaxed = true)

        source.booleanRead = PrefRead.Hit(true, "remote_test")
        assertTrue(PrefsReader.resolveBooleanWithSourcesForTest(context, "changed", false, listOf(source)).value)

        source.booleanRead = PrefRead.Hit(false, "remote_test")
        assertFalse(PrefsReader.resolveBooleanWithSourcesForTest(context, "changed", false, listOf(source)).value)
    }

    private class MutableSource : PrefsSource {
        override val sourceName: String = "remote_test"
        var booleanRead: PrefRead<Boolean> = PrefRead.Unavailable
        var stringRead: PrefRead<String> = PrefRead.Unavailable

        override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> = booleanRead

        override fun readString(key: String, defaultValue: String): PrefRead<String> = stringRead

        override fun readInt(key: String, defaultValue: Int): PrefRead<Int> = PrefRead.Unavailable
    }
}
