package io.github.magisk317.relay.ui.home

import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import io.github.magisk317.relay.ui.home.settings.SettingsHomeScreen
import io.github.magisk317.relay.ui.home.settings.SettingsViewModel

/**
 * Legacy compatibility shell. The main settings experience now lives in [SettingsHomeScreen].
 * Keep this wrapper only as a routing placeholder; avoid adding new behaviors here.
 */
@Composable
fun ComposeSettingsScreen(
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
    viewModel: SettingsViewModel? = null,
    refreshTrigger: Int = 0,
    onExit: () -> Unit = {},
) {
    SettingsHomeScreen(
        onOpenVerification = {},
        onOpenAdvancedRelay = {},
        onBack = onExit,
    )
}
