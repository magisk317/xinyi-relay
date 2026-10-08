@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.relay.ui.home.overview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur

/**
 * Expressive/Material implementation of the Overview page. Follows the XSC
 * `OverviewScreenMaterial` composition model (KernelSU `HomeMaterial` lineage):
 * page-owned `Scaffold` + collapsing top app bar driven by
 * `exitUntilCollapsedScrollBehavior`, with the glass top bar sampling the page
 * content recorded by the inner `Box`.
 */
@Composable
internal fun OverviewScreenMaterial(
    title: String,
    actions: @Composable RowScope.() -> Unit,
    body: @Composable (topContentPadding: Dp, nestedScrollConnection: NestedScrollConnection) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

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
                actions = actions,
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (glassOn) topGlass.contentRecorder() else Modifier)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            body(
                innerPadding.calculateTopPadding() + 8.dp,
                scrollBehavior.nestedScrollConnection,
            )
        }
    }
}
