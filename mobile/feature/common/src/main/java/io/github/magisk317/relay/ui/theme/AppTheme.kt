package io.github.magisk317.relay.ui.theme

import androidx.compose.runtime.Composable
import io.github.magisk317.uikit.theme.MagiskUiKitTheme
import io.github.magisk317.uikit.theme.UiKitColorSpec
import io.github.magisk317.uikit.theme.UiKitLayoutScale
import io.github.magisk317.uikit.theme.UiKitPaletteStyle
import io.github.magisk317.uikit.theme.UiKitStyle

/**
 * App-level theme wrapper mirroring [MagiskUiKitTheme]'s appearance surface.
 *
 * Integer parameters are storage indexes (see `SharedPreferenceKeys.Appearance`);
 * they are resolved through the ui-kit `fromValue` companions so unknown or
 * stale values fall back to the shipped defaults instead of crashing.
 */
@Composable
fun AppTheme(
    themeMode: Int,
    uiKitStyle: Int = UiKitStyle.Expressive.value,
    layoutScale: Int = UiKitLayoutScale.Standard.value,
    paletteStyle: Int = UiKitPaletteStyle.TonalSpot.value,
    colorSpec: Int = UiKitColorSpec.Spec2025.value,
    monetEnabled: Boolean = false,
    surfaceBlur: Boolean = false,
    dynamicColor: Boolean = true,
    accentColor: Int = 0,
    content: @Composable () -> Unit,
) {
    MagiskUiKitTheme(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        accentColor = accentColor,
        monetEnabled = monetEnabled,
        paletteStyle = UiKitPaletteStyle.fromValue(paletteStyle),
        colorSpec = UiKitColorSpec.fromValue(colorSpec),
        surfaceBlur = surfaceBlur,
        uiKitStyle = UiKitStyle.fromValue(uiKitStyle),
        layoutScale = UiKitLayoutScale.fromValue(layoutScale),
        content = content,
    )
}
