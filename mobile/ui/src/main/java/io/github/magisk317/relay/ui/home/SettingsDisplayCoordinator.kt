package io.github.magisk317.relay.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal data class SettingsDisplayActions(
    val onThemeClick: () -> Unit,
    val onLanguageClick: () -> Unit,
)

@Composable
internal fun rememberSettingsDisplayActions(
    settingsViewModel: SettingsViewModel,
    themeMode: Int,
    languageTag: String,
    notifySaved: () -> Unit,
): SettingsDisplayActions {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var themeDialogInitialMode by remember { mutableStateOf(0) }
    var themeDialogSelectedMode by remember { mutableStateOf(0) }
    var languageDialogInitialTag by remember { mutableStateOf("") }
    var languageDialogSelectedTag by remember { mutableStateOf("") }

    if (showThemeDialog) {
        SettingsThemeDialog(
            selectedMode = themeDialogSelectedMode,
            onDismiss = {
                settingsViewModel.previewThemeMode(themeDialogInitialMode)
                showThemeDialog = false
            },
            onSelectionChange = { index ->
                themeDialogSelectedMode = index
                settingsViewModel.previewThemeMode(index)
            },
            onConfirm = { index ->
                showThemeDialog = false
                themeDialogSelectedMode = index
                settingsViewModel.persistThemeMode(index)
                notifySaved()
            },
        )
    }
    if (showLanguageDialog) {
        SettingsLanguageDialog(
            selectedTag = languageDialogSelectedTag,
            onDismiss = {
                settingsViewModel.previewLanguageTag(languageDialogInitialTag)
                showLanguageDialog = false
            },
            onSelectionChange = { tag ->
                languageDialogSelectedTag = tag
                settingsViewModel.previewLanguageTag(tag)
            },
            onConfirm = { tag ->
                showLanguageDialog = false
                languageDialogSelectedTag = tag
                settingsViewModel.persistLanguageTag(tag)
                notifySaved()
            },
        )
    }

    return SettingsDisplayActions(
        onThemeClick = {
            themeDialogInitialMode = themeMode
            themeDialogSelectedMode = themeMode
            showThemeDialog = true
        },
        onLanguageClick = {
            languageDialogInitialTag = languageTag
            languageDialogSelectedTag = languageTag
            showLanguageDialog = true
        },
    )
}
