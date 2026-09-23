package io.github.magisk317.relay.ui.home.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.mobilefeature.settings.BuildConfig
import io.github.magisk317.relay.ui.theme.ThemeAccent
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.uikit.preference.SettingsChoiceRow
import io.github.magisk317.uikit.preference.StateSwitchItem
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.uikit.theme.UiKitColorSpec
import io.github.magisk317.uikit.theme.UiKitLayoutScale
import io.github.magisk317.uikit.theme.UiKitPaletteStyle
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle

/**
 * Secondary page that owns the appearance choices that used to live inline in the General
 * group: theme mode and, when the switch is gated on, the UI kit style.
 *
 * Both rows render through the shared [SettingsChoiceRow], which owns its own expand/dismiss
 * state and picks the picker that matches the active style. The theme row additionally reports
 * the tapped screen position so MainActivity can run the reveal animation from the click point;
 * because the miuix dropdown carries no coordinate semantics, that fallback path funnels into
 * [SettingsViewModel.persistThemeMode]'s no-coordinate overload and the reveal falls back to
 * the screen centre.
 */
@Composable
fun ThemeSettingsPage(
    onBack: () -> Unit,
) {
    val settingsViewModel = rememberSharedSettingsViewModel()
    val themeState by settingsViewModel.themeState.collectAsStateWithLifecycle()

    val themeOptions = listOf(
        stringResource(id = R.string.theme_follow_system),
        stringResource(id = R.string.theme_light),
        stringResource(id = R.string.theme_dark),
        stringResource(id = R.string.theme_black),
    )
    val uiKitStyleOptions = listOf(
        stringResource(id = R.string.ui_kit_style_expressive),
        stringResource(id = R.string.ui_kit_style_miuix),
    )
    val layoutScaleOptions = listOf(
        stringResource(id = R.string.layout_scale_compact),
        stringResource(id = R.string.layout_scale_standard),
        stringResource(id = R.string.layout_scale_comfortable),
    )
    val paletteStyleOptions = listOf(
        stringResource(id = R.string.palette_style_tonal_spot),
        stringResource(id = R.string.palette_style_neutral),
        stringResource(id = R.string.palette_style_vibrant),
        stringResource(id = R.string.palette_style_expressive),
        stringResource(id = R.string.palette_style_rainbow),
        stringResource(id = R.string.palette_style_fruit_salad),
        stringResource(id = R.string.palette_style_monochrome),
        stringResource(id = R.string.palette_style_fidelity),
        stringResource(id = R.string.palette_style_content),
    )
    val colorSpecOptions = listOf(
        stringResource(id = R.string.color_spec_2021),
        stringResource(id = R.string.color_spec_2025),
    )
    val accentOptions = listOf(
        stringResource(id = R.string.accent_system),
        stringResource(id = R.string.accent_blue),
        stringResource(id = R.string.accent_purple),
        stringResource(id = R.string.accent_green),
        stringResource(id = R.string.accent_orange),
        stringResource(id = R.string.accent_rose),
    )
    val selectedThemeIndex = themeState.mode.coerceIn(themeOptions.indices)
    val selectedUiKitStyleIndex = themeState.uiKitStyle.coerceIn(uiKitStyleOptions.indices)
    val selectedLayoutScaleIndex = UiKitLayoutScale.fromValue(themeState.layoutScale).value
    val selectedPaletteStyleIndex = UiKitPaletteStyle.fromValue(themeState.paletteStyle).value
    val selectedColorSpecIndex = UiKitColorSpec.fromValue(themeState.colorSpec).value
    val selectedAccentIndex = ThemeAccent.fromArgb(themeState.accentColor).value
    val isMiuixStyle = currentUiKitStyle() == UiKitStyle.Miuix

    val body: @Composable (PaddingValues, Modifier) -> Unit = { listPadding, scrollModifier ->
        SectionColumn(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(scrollModifier),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = listPadding.calculateTopPadding() + 8.dp,
                end = 12.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(
                title = stringResource(id = R.string.pref_theme_group_appearance_title),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
                SettingsChoiceRow(
                    title = stringResource(id = R.string.pref_choose_theme_title),
                    summary = themeOptions[selectedThemeIndex],
                    options = themeOptions,
                    selectedIndex = selectedThemeIndex,
                    onSelect = { index -> settingsViewModel.persistThemeMode(index) },
                    onSelectWithPosition = { index, x, y ->
                        settingsViewModel.persistThemeMode(index, x, y)
                    },
                )
                if (BuildConfig.ENABLE_UI_KIT_STYLE_SWITCH) {
                    SettingsChoiceRow(
                        title = stringResource(id = R.string.pref_ui_kit_style_title),
                        summary = uiKitStyleOptions[selectedUiKitStyleIndex],
                        options = uiKitStyleOptions,
                        selectedIndex = selectedUiKitStyleIndex,
                        onSelect = { index -> settingsViewModel.persistUiKitStyle(index) },
                    )
                }
                SettingsChoiceRow(
                    title = stringResource(id = R.string.pref_layout_scale_title),
                    summary = layoutScaleOptions[selectedLayoutScaleIndex],
                    options = layoutScaleOptions,
                    selectedIndex = selectedLayoutScaleIndex,
                    onSelect = { index -> settingsViewModel.setLayoutScale(index) },
                )
            }

            SectionCard(
                title = stringResource(id = R.string.pref_theme_group_navigation),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_floating_bottom_bar_title),
                    summary = if (isMiuixStyle) {
                        stringResource(id = R.string.pref_floating_bottom_bar_summary_miuix)
                    } else {
                        stringResource(id = R.string.pref_floating_bottom_bar_summary)
                    },
                    checked = themeState.floatingBottomBar,
                ) { enabled ->
                    settingsViewModel.setFloatingBottomBarEnabled(enabled)
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_bottom_bar_blur_title),
                    summary = stringResource(id = R.string.pref_bottom_bar_blur_summary),
                    checked = themeState.bottomBarBlur,
                    enabled = themeState.floatingBottomBar && isMiuixStyle,
                ) { enabled ->
                    settingsViewModel.setBottomBarBlurEnabled(enabled)
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_bottom_bar_backdrop_title),
                    summary = stringResource(id = R.string.pref_bottom_bar_backdrop_summary),
                    checked = themeState.bottomBarBackdrop,
                    enabled = themeState.floatingBottomBar && themeState.bottomBarBlur &&
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        isMiuixStyle,
                ) { enabled ->
                    settingsViewModel.setBottomBarBackdropEnabled(enabled)
                }
            }

            SectionCard(
                title = stringResource(id = R.string.pref_theme_group_color),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_monet_title),
                    summary = stringResource(id = R.string.pref_monet_summary),
                    checked = themeState.monetEnabled,
                ) { enabled ->
                    settingsViewModel.setMonetEnabled(enabled)
                }
                SettingsChoiceRow(
                    title = stringResource(id = R.string.pref_palette_style_title),
                    summary = paletteStyleOptions[selectedPaletteStyleIndex],
                    options = paletteStyleOptions,
                    selectedIndex = selectedPaletteStyleIndex,
                    onSelect = { index -> settingsViewModel.setPaletteStyle(index) },
                )
                SettingsChoiceRow(
                    title = stringResource(id = R.string.pref_color_spec_title),
                    summary = colorSpecOptions[selectedColorSpecIndex],
                    options = colorSpecOptions,
                    selectedIndex = selectedColorSpecIndex,
                    onSelect = { index -> settingsViewModel.setColorSpec(index) },
                )
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_surface_blur_title),
                    summary = stringResource(id = R.string.pref_surface_blur_summary),
                    checked = themeState.surfaceBlur,
                ) { enabled ->
                    settingsViewModel.setSurfaceBlurEnabled(enabled)
                }
                StateSwitchItem(
                    title = stringResource(id = R.string.pref_dynamic_color_title),
                    summary = stringResource(id = R.string.pref_dynamic_color_summary),
                    checked = themeState.dynamicColor,
                    enabled = selectedAccentIndex == ThemeAccent.System.value,
                ) { enabled ->
                    settingsViewModel.setDynamicColorEnabled(enabled)
                }
                SettingsChoiceRow(
                    title = stringResource(id = R.string.pref_accent_color_title),
                    summary = accentOptions[selectedAccentIndex],
                    options = accentOptions,
                    selectedIndex = selectedAccentIndex,
                    onSelect = { index ->
                        ThemeAccent.fromValue(index).colorArgb?.let(settingsViewModel::setAccentColor)
                            ?: settingsViewModel.setAccentColor(0)
                    },
                )
            }
        }
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> ThemeSettingsMiuix(onBack = onBack, body = body)
        UiKitStyle.Expressive -> ThemeSettingsExpressive(onBack = onBack, body = body)
    }
}
