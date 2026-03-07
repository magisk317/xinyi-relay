package com.github.magisk317.smscode.ui.app.base

import android.app.Activity
import android.os.Build
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

private const val LIGHT_NAV_SCRIM = 0xE6FFFFFF.toInt()
private const val DARK_NAV_SCRIM = 0x801B1B1B.toInt()

fun applyEdgeToEdge(activity: ComponentActivity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        // Android 15+: avoid deprecated edge-to-edge setters flagged by Play pre-launch checks.
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    } else {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = LIGHT_NAV_SCRIM,
                darkScrim = DARK_NAV_SCRIM,
            ),
        )
    }
}

@Composable
fun SystemBarsScrim(hazeState: HazeState, hazeStyle: HazeStyle) {
    Box(modifier = Modifier.fillMaxSize()) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .hazeEffect(hazeState, hazeStyle) {
                    forceInvalidateOnPreDraw = true
                }
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
        )
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .align(Alignment.BottomStart)
                .hazeEffect(hazeState, hazeStyle) {
                    forceInvalidateOnPreDraw = true
                }
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
        )
    }
}

@Composable
fun rememberHazeStyle(
    blurRadius: androidx.compose.ui.unit.Dp = 25.dp,
    tintAlpha: Float = 0.2f
): HazeStyle = HazeStyle(
    backgroundColor = MaterialTheme.colorScheme.surface,
    tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = tintAlpha)),
    blurRadius = blurRadius,
    noiseFactor = 0.1f,
)

@Composable
fun UpdateSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) {
        return
    }
    val window = (view.context as Activity).window
    SideEffect {
        val controller = WindowInsetsControllerCompat(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}
