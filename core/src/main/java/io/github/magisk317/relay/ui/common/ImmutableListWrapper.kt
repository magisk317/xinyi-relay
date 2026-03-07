package io.github.magisk317.relay.ui.common

import androidx.compose.runtime.Immutable

/**
 * A stable wrapper for lists to enable Recomposition Skipping for Composable functions
 * that take a list as a parameter. Standard Kotlin Lists are considered unstable by
 * the Compose compiler.
 */
@Immutable
data class ImmutableListWrapper<T>(val items: List<T> = emptyList())
