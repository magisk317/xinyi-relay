package io.github.magisk317.relay.xpbridge.bridge

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
