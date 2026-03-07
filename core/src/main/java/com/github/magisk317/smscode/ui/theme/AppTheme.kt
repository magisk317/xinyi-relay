package com.github.magisk317.smscode.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.github.magisk317.smscode.ui.app.base.UpdateSystemBars

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun AppTheme(themeMode: Int, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isPureBlack = themeMode == 3
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        3 -> true
        else -> isSystemInDarkTheme()
    }

    UpdateSystemBars(darkTheme)

    val darkBaseColorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme()
    }

    val pureBlackColorScheme = darkBaseColorScheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
    )

    // Material 3 Expressive Theme Implementation
    MaterialExpressiveTheme(
        colorScheme = when {
            isPureBlack -> pureBlackColorScheme
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
            darkTheme -> darkColorScheme()
            else -> lightColorScheme()
        },
        content = content,
    )
}
