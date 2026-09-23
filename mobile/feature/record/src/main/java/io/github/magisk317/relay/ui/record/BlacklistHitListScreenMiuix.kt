package io.github.magisk317.relay.ui.record

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import io.github.magisk317.uikit.surface.DoubleTapToTopOverlay
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import top.yukonga.miuix.kmp.basic.Scaffold

/**
 * Miuix chrome for [BlacklistHitListScreen]: static miuix top bar hosted in a
 * page-owned miuix `Scaffold`, with the glass top bar sampling the page
 * content recorded by the inner `Box` (same glass model as
 * OverviewScreenMiuix). The list receives its top inset from the scaffold so
 * content never hides under the bar.
 */
@Composable
internal fun BlacklistHitListScreenMiuix(
    title: String,
    onBack: () -> Unit,
    onOpenClear: () -> Unit,
    onOpenSettings: () -> Unit,
    clearActionEnabled: Boolean,
    listState: LazyListState,
    body: @Composable (PaddingValues) -> Unit,
) {
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    val scrollScope = rememberCoroutineScope()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Scaffold(
        topBar = {
            Box {
                AppTopBar(
                    modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                    containerColor = if (glassOn) Color.Transparent else chromeSurfaceColor(),
                    title = title,
                    navigationIcon = {
                        AppIconButton(onClick = onBack) {
                            AppIcon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        AppIconButton(
                            onClick = onOpenClear,
                            enabled = clearActionEnabled,
                        ) {
                            AppIcon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(
                                    R.string.action_clear_records_content_description,
                                ),
                            )
                        }
                        AppIconButton(onClick = onOpenSettings) {
                            AppIcon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.pref_code_records_title),
                            )
                        }
                    },
                    windowInsets = WindowInsets.statusBars,
                )
                DoubleTapToTopOverlay(
                    onDoubleTap = {
                        scrollScope.launch { listState.animateScrollToItem(0) }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 72.dp, top = statusBarTop, end = 112.dp)
                        .fillMaxWidth()
                        .height(64.dp),
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (glassOn) topGlass.contentRecorder() else Modifier),
        ) {
            body(PaddingValues(top = innerPadding.calculateTopPadding()))
            ScrollToTopFAB(listState = listState)
        }
    }
}
