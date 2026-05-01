package io.github.magisk317.relay.contract.prefs

import android.content.Context

data class PrefReadResult<T>(
    val value: T,
    val source: String,
)

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

data class XpCapabilities(
    val frameworkName: String,
    val frameworkVersion: String,
    val frameworkApiVersion: Int? = null,
    val frameworkPrivilege: Int? = null,
    val frameworkProperties: Long? = null,
    val supportsRemotePrefs: Boolean = false,
    val supportsRemoteFile: Boolean = false,
    val supportsDeopt: Boolean = false,
) {
    fun hasFrameworkProperty(flag: Long): Boolean = frameworkProperties?.let { props ->
        props and flag != 0L
    } ?: false

    companion object {
        const val PROP_CAP_SYSTEM: Long = 1L
        const val PROP_CAP_REMOTE: Long = 1L shl 1
        const val PROP_RT_API_PROTECTION: Long = 1L shl 2
    }
}

interface XpRuntimeBridge {
    fun capabilities(): XpCapabilities

    fun remotePrefsSource(group: String): PrefsSource = NoopRemotePrefsSource
}

object NoopXpRuntimeBridge : XpRuntimeBridge {
    override fun capabilities(): XpCapabilities = XpCapabilities(
        frameworkName = "libxposed",
        frameworkVersion = "unknown",
        frameworkApiVersion = null,
        frameworkPrivilege = null,
        frameworkProperties = null,
        supportsRemotePrefs = false,
        supportsRemoteFile = false,
        supportsDeopt = false,
    )
}
