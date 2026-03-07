package io.github.magisk317.relay.xp.runtime

import io.github.magisk317.relay.common.xp.NoopRemotePrefsSource
import io.github.magisk317.relay.common.xp.PrefsSource
import io.github.magisk317.relay.common.xp.XpCapabilities
import io.github.magisk317.relay.common.xp.XpRuntimeBridge
import de.robv.android.xposed.XposedBridge

class LegacyXposedRuntimeBridge : XpRuntimeBridge {
    private val snapshot: XpCapabilities by lazy { resolveCapabilities() }

    override fun capabilities(): XpCapabilities = snapshot

    override fun remotePrefsSource(group: String): PrefsSource = NoopRemotePrefsSource

    private fun resolveCapabilities(): XpCapabilities {
        val versionCode = runCatching {
            XposedBridge::class.java.getMethod("getXposedVersion").invoke(null) as Int
        }.getOrElse {
            runCatching {
                val field = XposedBridge::class.java.getDeclaredField("XPOSED_BRIDGE_VERSION")
                field.isAccessible = true
                field.getInt(null)
            }.getOrDefault(-1)
        }
        return XpCapabilities(
            frameworkName = "de.robv.android.xposed",
            frameworkVersion = if (versionCode > 0) versionCode.toString() else "unknown",
            frameworkApiVersion = null,
            frameworkPrivilege = null,
            frameworkProperties = null,
            supportsRemotePrefs = false,
            supportsRemoteFile = false,
            supportsDeopt = false,
        )
    }
}
