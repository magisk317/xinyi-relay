package io.github.magisk317.relay.prefs.bridge

import android.content.Context

data class PrefReadResult<T>(val value: T, val source: String)

interface PrefsSource {
    val sourceName: String

    fun readBoolean(context: Context, key: String, defaultValue: Boolean): PrefReadResult<Boolean>?

    fun readString(context: Context, key: String, defaultValue: String): PrefReadResult<String>?

    fun readInt(context: Context, key: String, defaultValue: Int): PrefReadResult<Int>?
}

object NoopRemotePrefsSource : PrefsSource {
    override val sourceName: String = "remote_noop"

    override fun readBoolean(context: Context, key: String, defaultValue: Boolean): PrefReadResult<Boolean>? = null

    override fun readString(context: Context, key: String, defaultValue: String): PrefReadResult<String>? = null

    override fun readInt(context: Context, key: String, defaultValue: Int): PrefReadResult<Int>? = null
}
