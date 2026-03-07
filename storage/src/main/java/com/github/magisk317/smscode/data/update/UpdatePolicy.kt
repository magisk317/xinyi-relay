package com.github.magisk317.smscode.data.update

object UpdatePolicy {

    enum class StartupTarget {
        PLAY,
        GITHUB,
    }

    fun shouldRunAutoCheck(enabled: Boolean, wifiOnly: Boolean, onWifi: Boolean): Boolean =
        enabled && (!wifiOnly || onWifi)

    fun resolveStartupTarget(installedFromPlay: Boolean): StartupTarget =
        if (installedFromPlay) StartupTarget.PLAY else StartupTarget.GITHUB

    fun shouldSkipGithubCheckOnStartup(
        installedFromPlay: Boolean,
        autoCheckEnabled: Boolean,
        wifiOnly: Boolean,
        onWifi: Boolean,
    ): Boolean = installedFromPlay || !shouldRunAutoCheck(autoCheckEnabled, wifiOnly, onWifi)

    fun shouldSkipIgnoredVersion(respectIgnoredVersion: Boolean, ignoredVersion: String, latestVersion: String): Boolean =
        respectIgnoredVersion && ignoredVersion == latestVersion
}
