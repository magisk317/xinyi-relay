package io.github.magisk317.relay.prefs

import android.content.Context
import dev.mokkery.MockMode.autofill
import dev.mokkery.mock
import io.github.magisk317.relay.contract.prefs.PrefReadResult
import io.github.magisk317.relay.contract.prefs.PrefsSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrefsReaderSourceChainTest {

    @Test
    fun resolveBoolean_prefersRemoteThenProviderThenSharedThenDefault() {
        val context = mock<Context>(autofill)
        val remote = fakeSource(
            name = "remote",
            boolResult = PrefReadResult(true, "remote"),
        )
        val provider = fakeSource(
            name = "provider",
            boolResult = PrefReadResult(false, "provider"),
        )
        val shared = fakeSource(
            name = "shared_prefs",
            boolResult = PrefReadResult(false, "shared_prefs"),
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
    fun resolveBoolean_fallbacksWhenEarlierSourceFails() {
        val context = mock<Context>(autofill)
        val remoteThrows = object : PrefsSource {
            override val sourceName: String = "remote"

            override fun readBoolean(context: Context, key: String, defaultValue: Boolean): PrefReadResult<Boolean>? {
                error("remote unavailable")
            }

            override fun readString(context: Context, key: String, defaultValue: String): PrefReadResult<String>? = null

            override fun readInt(context: Context, key: String, defaultValue: Int): PrefReadResult<Int>? = null
        }
        val provider = fakeSource(
            name = "provider",
            boolResult = PrefReadResult(true, "provider"),
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
    fun resolveString_fallbackToProviderWhenRemoteIsNull() {
        val context = mock<Context>(autofill)
        val remote = fakeSource(name = "remote", stringResult = null)
        val provider = fakeSource(
            name = "provider",
            stringResult = PrefReadResult("v_from_provider", "provider"),
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
        val context = mock<Context>(autofill)
        val remote = fakeSource(name = "remote", intResult = null)
        val provider = fakeSource(name = "provider", intResult = null)
        val shared = fakeSource(name = "shared_prefs", intResult = null)

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
        boolResult: PrefReadResult<Boolean>? = null,
        stringResult: PrefReadResult<String>? = null,
        intResult: PrefReadResult<Int>? = null,
    ): PrefsSource = object : PrefsSource {
        override val sourceName: String = name

        override fun readBoolean(context: Context, key: String, defaultValue: Boolean): PrefReadResult<Boolean>? = boolResult

        override fun readString(context: Context, key: String, defaultValue: String): PrefReadResult<String>? = stringResult

        override fun readInt(context: Context, key: String, defaultValue: Int): PrefReadResult<Int>? = intResult
    }
}
