@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.relay.ui.home.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur

/**
 * Expressive/Material chrome for the advanced relay settings tab of
 * the same name: static top bar hosted in a page-owned `Scaffold`; the
 * glass top bar samples the page content recorded by the inner `Box`.
 * The tab page has no back navigation, no actions and no snackbar.
 * This chrome is a pure counterpart of the original screen: body and
 * helpers are preserved verbatim.
 */
@Composable
internal fun AdvancedScreenMaterial(
    title: String,
    body: @Composable (PaddingValues) -> Unit
) {
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                containerColor = if (glassOn) Color.Transparent else chromeSurfaceColor(),
                title = title,
                windowInsets = WindowInsets.statusBars,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (glassOn) topGlass.contentRecorder() else Modifier),
        ) {
            body(PaddingValues(top = innerPadding.calculateTopPadding()))
        }
    }
}
