package io.github.magisk317.relay.ui.home.settings

internal data class SettingsPageWorkPolicy(
    val loadSnapshots: Boolean,
    val inspectLauncherIcon: Boolean,
    val publishLauncherMirror: Boolean,
    val collectBackupEvents: Boolean,
    val inspectBackup: Boolean,
)

internal fun settingsPageWorkPolicy(isActive: Boolean): SettingsPageWorkPolicy =
    SettingsPageWorkPolicy(
        loadSnapshots = isActive,
        inspectLauncherIcon = isActive,
        publishLauncherMirror = isActive,
        collectBackupEvents = isActive,
        inspectBackup = isActive,
    )

internal data class AdvancedPageWorkPolicy(
    val enableScrolling: Boolean,
    val exposeBenchmarkTags: Boolean,
)

internal fun advancedPageWorkPolicy(
    isActive: Boolean,
    benchmarkTagsEnabled: Boolean,
): AdvancedPageWorkPolicy = AdvancedPageWorkPolicy(
    enableScrolling = isActive,
    exposeBenchmarkTags = isActive && benchmarkTagsEnabled,
)
