package io.github.magisk317.relay.ui.home.settings

import androidx.compose.runtime.Composable

internal data class SettingsDisplayActions(
    val onThemeSelected: (Int, Float, Float) -> Unit,
    val onLanguageSelected: (String) -> Unit,
)

/**
 * Wires the UI Kit general-settings section's theme/language selections into
 * the view model. The dialogs themselves (and per-app locale persistence)
 * live inside the UI Kit GeneralSettingsSection.
 */
@Composable
internal fun rememberSettingsDisplayActions(
    settingsViewModel: SettingsViewModel,
    notifySaved: () -> Unit,
): SettingsDisplayActions {
    return SettingsDisplayActions(
        onThemeSelected = { index, x, y ->
            settingsViewModel.persistThemeMode(index, x, y)
            notifySaved()
        },
        onLanguageSelected = { tag ->
            settingsViewModel.persistLanguageTag(tag)
            notifySaved()
        },
    )
}
