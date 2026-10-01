package io.github.magisk317.relay.contract.prefs

import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead

typealias PrefReadResult<T> = io.github.magisk317.smscode.runtime.contract.prefs.PrefReadResult<T>
typealias PrefsSource = io.github.magisk317.smscode.runtime.contract.prefs.PrefsSource

object NoopRemotePrefsSource : PrefsSource {
    override val sourceName: String = "remote_noop"

    override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> = PrefRead.Unavailable

    override fun readString(key: String, defaultValue: String): PrefRead<String> = PrefRead.Unavailable

    override fun readInt(key: String, defaultValue: Int): PrefRead<Int> = PrefRead.Unavailable
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
