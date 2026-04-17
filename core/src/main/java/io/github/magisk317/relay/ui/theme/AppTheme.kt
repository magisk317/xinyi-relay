package io.github.magisk317.relay.ui.theme

import androidx.compose.runtime.Composable
import io.github.magisk317.uikit.theme.MagiskUiKitTheme
import io.github.magisk317.uikit.theme.UiKitStyle

@Composable
fun AppTheme(
    themeMode: Int,
    uiKitStyle: Int = UiKitStyle.Expressive.value,
    content: @Composable () -> Unit,
) {
    MagiskUiKitTheme(
        themeMode = themeMode,
        uiKitStyle = UiKitStyle.fromValue(uiKitStyle),
        content = content,
    )
}
