package io.github.magisk317.relay.ui.home.settings

import androidx.compose.runtime.Composable

internal data class SettingsDisplayActions(
    val onLanguageSelected: (String) -> Unit,
)

/**
 * Wires the UI Kit general-settings section's language selection into the view
 * model. The language dialog and per-app locale persistence live inside the UI
 * Kit GeneralSettingsSection; theme mode and UI kit style now live on
 * ThemeSettingsPage and talk to the view model directly.
 */
@Composable
internal fun rememberSettingsDisplayActions(
    settingsViewModel: SettingsViewModel,
    notifySaved: () -> Unit,
): SettingsDisplayActions {
    return SettingsDisplayActions(
        onLanguageSelected = { tag ->
            settingsViewModel.persistLanguageTag(tag)
            notifySaved()
        },
    )
}
