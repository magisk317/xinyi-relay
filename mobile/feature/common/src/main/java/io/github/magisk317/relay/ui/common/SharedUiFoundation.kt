package io.github.magisk317.relay.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

typealias ImmutableListWrapper<T> = io.github.magisk317.uikit.foundation.ImmutableListWrapper<T>

@Composable
fun AppLinearLoadingIndicator(modifier: Modifier = Modifier) {
    io.github.magisk317.uikit.foundation.AppLinearLoadingIndicator(modifier = modifier)
}

val LocalSnackbarHostState = io.github.magisk317.uikit.foundation.LocalSnackbarHostState
