package io.github.magisk317.relay.ui.home.overview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar

/**
 * Miuix implementation of the Overview page. Follows the XSC
 * `OverviewScreenMiuix` composition model (KernelSU `HomeMiuix` lineage):
 * page-owned miuix `Scaffold` + collapsing `TopAppBar` driven by
 * `MiuixScrollBehavior`, with the glass top bar sampling the page content
 * recorded by the inner `Box`.
 */
@Composable
internal fun OverviewScreenMiuix(
    title: String,
    actions: @Composable RowScope.() -> Unit,
    body: @Composable (topContentPadding: Dp, nestedScrollConnection: NestedScrollConnection) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()

    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                title = title,
                actions = actions,
                scrollBehavior = scrollBehavior,
                color = if (glassOn) Color.Transparent else chromeSurfaceColor(),
                defaultWindowInsetsPadding = true,
            )
        },
        contentWindowInsets = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (glassOn) topGlass.contentRecorder() else Modifier)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            body(
                innerPadding.calculateTopPadding() + 12.dp,
                scrollBehavior.nestedScrollConnection,
            )
        }
    }
}
