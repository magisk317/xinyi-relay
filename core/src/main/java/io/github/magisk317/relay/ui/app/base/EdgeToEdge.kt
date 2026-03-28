package io.github.magisk317.relay.ui.app.base

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
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import io.github.magisk317.uikit.theme.UpdateSystemBars as UpdateUiKitSystemBars
import io.github.magisk317.uikit.theme.applyEdgeToEdge as applyUiKitEdgeToEdge

fun applyEdgeToEdge(activity: ComponentActivity) {
    applyUiKitEdgeToEdge(activity)
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
    UpdateUiKitSystemBars(darkTheme)
}
