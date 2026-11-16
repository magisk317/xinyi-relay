package io.github.magisk317.relay.feature.mode

/** Runtime identity shown on the overview status card, in priority order. */
sealed interface StatusCardState {
    data class Framework(val identity: FrameworkIdentity) : StatusCardState
    data class Shizuku(val version: String?) : StatusCardState
    data object Inactive : StatusCardState
}

fun overviewStatusCard(
    xposedActive: Boolean,
    framework: FrameworkIdentity,
    nonRootBuild: Boolean,
    shizukuOnline: Boolean,
    shizukuAuthorized: Boolean,
    shizukuVersion: String?,
): StatusCardState = when {
    xposedActive -> StatusCardState.Framework(framework)
    nonRootBuild && shizukuOnline && shizukuAuthorized -> StatusCardState.Shizuku(shizukuVersion)
    else -> StatusCardState.Inactive
}
