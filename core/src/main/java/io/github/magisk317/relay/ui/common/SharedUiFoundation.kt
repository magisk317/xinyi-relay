package io.github.magisk317.relay.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

typealias ImmutableListWrapper<T> = io.github.magisk317.uikit.foundation.ImmutableListWrapper<T>

object LoadingIndicatorTokens {
    val MIN_VISIBLE_DURATION_MILLIS: Long =
        io.github.magisk317.uikit.foundation.LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS
    val OverlayTopSpacing = io.github.magisk317.uikit.foundation.LoadingIndicatorTokens.OverlayTopSpacing
    val ContainedSize = io.github.magisk317.uikit.foundation.LoadingIndicatorTokens.ContainedSize
}

@Composable
fun AppLinearLoadingIndicator(modifier: Modifier = Modifier) {
    io.github.magisk317.uikit.foundation.AppLinearLoadingIndicator(modifier = modifier)
}

@Composable
fun rememberMinDurationLoading(
    actualLoading: Boolean,
    minDurationMillis: Long = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
): Boolean {
    return io.github.magisk317.uikit.foundation.rememberMinDurationLoading(
        actualLoading = actualLoading,
        minDurationMillis = minDurationMillis,
    )
}

val LocalSnackbarHostState = io.github.magisk317.uikit.foundation.LocalSnackbarHostState

@Composable
fun PolygonMorphLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = LoadingIndicatorTokens.ContainedSize,
) {
    io.github.magisk317.uikit.foundation.PolygonMorphLoadingIndicator(
        modifier = modifier,
        size = size,
    )
}

object SessionLoadingRegistry {
    fun shouldShowInitial(key: String): Boolean =
        io.github.magisk317.uikit.foundation.SessionLoadingRegistry.shouldShowInitial(key)

    fun markShown(key: String) {
        io.github.magisk317.uikit.foundation.SessionLoadingRegistry.markShown(key)
    }
}
