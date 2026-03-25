package io.github.magisk317.relay.ui.theme

import androidx.compose.runtime.Composable
import io.github.magisk317.uikit.theme.MagiskUiKitTheme

@Composable
fun AppTheme(themeMode: Int, content: @Composable () -> Unit) {
    MagiskUiKitTheme(themeMode = themeMode, content = content)
}
