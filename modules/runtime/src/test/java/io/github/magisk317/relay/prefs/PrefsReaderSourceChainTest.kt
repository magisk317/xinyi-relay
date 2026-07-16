package io.github.magisk317.relay.prefs

import android.content.Context
import android.os.SystemClock
import io.github.magisk317.relay.android.prefs.PrefsReader
import io.github.magisk317.relay.contract.prefs.PrefReadResult
import io.github.magisk317.relay.contract.prefs.PrefsSource
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PrefsReaderSourceChainTest {

    @BeforeEach
    fun setUpClock() {
        mockkStatic(SystemClock::class)
        io.mockk.every { SystemClock.elapsedRealtime() } returns 1_000L
        PrefsReader.invalidateCache()
    }

    @AfterEach
    fun tearDownClock() {
        PrefsReader.invalidateCache()
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun resolveBoolean_prefersRemoteThenProviderThenSharedThenDefault() {
        val context = mockk<Context>(relaxed = true)
        val remote = fakeSource(
            name = "remote",
            boolResult = PrefRead.Hit(true, "remote"),
        )
        val provider = fakeSource(
            name = "provider",
            boolResult = PrefRead.Hit(false, "provider"),
        )
        val shared = fakeSource(
            name = "shared_prefs",
            boolResult = PrefRead.Hit(false, "shared_prefs"),
        )

        val result = PrefsReader.resolveBooleanWithSourcesForTest(
            context = context,
            key = "pref_key_remote_bool",
            defaultValue = false,
            sources = listOf(remote, provider, shared),
        )

        assertTrue(result.value)
        assertEquals("remote", result.source)
    }

    @Test
    fun resolveBoolean_fallbacksWhenEarlierSourceUnavailable() {
        val context = mockk<Context>(relaxed = true)
        val remoteThrows = object : PrefsSource {
            override val sourceName: String = "remote"

            override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> = PrefRead.Unavailable

            override fun readString(key: String, defaultValue: String): PrefRead<String> = PrefRead.Unavailable

            override fun readInt(key: String, defaultValue: Int): PrefRead<Int> = PrefRead.Unavailable
        }
        val provider = fakeSource(
            name = "provider",
            boolResult = PrefRead.Hit(true, "provider"),
        )

        val result = PrefsReader.resolveBooleanWithSourcesForTest(
            context = context,
            key = "pref_key_provider_bool",
            defaultValue = false,
            sources = listOf(remoteThrows, provider),
        )

        assertTrue(result.value)
        assertEquals("provider", result.source)
    }

    @Test
    fun resolveBoolean_fallbacksWhenRemoteMisses() {
        val context = mockk<Context>(relaxed = true)
        val remoteMiss = fakeSource(name = "remote", boolResult = PrefRead.Miss)
        val local = fakeSource(
            name = "local_hook_prefs",
            boolResult = PrefRead.Hit(true, "local_hook_prefs"),
        )

        val result = PrefsReader.resolveBooleanWithSourcesForTest(
            context = context,
            key = "pref_key_remote_miss_local_bool",
            defaultValue = false,
            sources = listOf(remoteMiss, local),
        )

        assertTrue(result.value)
        assertEquals("local_hook_prefs", result.source)
    }

    @Test
    fun resolveString_fallbackToProviderWhenRemoteMisses() {
        val context = mockk<Context>(relaxed = true)
        val remote = fakeSource(name = "remote", stringResult = PrefRead.Miss)
        val provider = fakeSource(
            name = "provider",
            stringResult = PrefRead.Hit("v_from_provider", "provider"),
        )

        val result = PrefsReader.resolveStringWithSourcesForTest(
            context = context,
            key = "pref_key_provider_string",
            defaultValue = "default",
            sources = listOf(remote, provider),
        )

        assertEquals("v_from_provider", result.value)
        assertEquals("provider", result.source)
    }

    @Test
    fun resolveInt_returnsDefaultWhenAllSourcesMiss() {
        val context = mockk<Context>(relaxed = true)
        val remote = fakeSource(name = "remote", intResult = PrefRead.Miss)
        val provider = fakeSource(name = "provider", intResult = PrefRead.Miss)
        val shared = fakeSource(name = "shared_prefs", intResult = PrefRead.Miss)

        val result = PrefsReader.resolveIntWithSourcesForTest(
            context = context,
            key = "pref_key_default_int",
            defaultValue = 7,
            sources = listOf(remote, provider, shared),
        )

        assertEquals(7, result.value)
        assertEquals("default", result.source)
    }

    private fun fakeSource(
        name: String,
        boolResult: PrefRead<Boolean> = PrefRead.Miss,
        stringResult: PrefRead<String> = PrefRead.Miss,
        intResult: PrefRead<Int> = PrefRead.Miss,
    ): PrefsSource = object : PrefsSource {
        override val sourceName: String = name

        override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> = boolResult

        override fun readString(key: String, defaultValue: String): PrefRead<String> = stringResult

        override fun readInt(key: String, defaultValue: Int): PrefRead<Int> = intResult
    }
}
