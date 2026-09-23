@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.relay.ui.sender

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import io.github.magisk317.uikit.surface.DoubleTapToTopOverlay
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.surface.AppFloatingActionButton

/**
 * Expressive/Material chrome for the sender list screen of the same
 * name: static top bar hosted in a page-owned `Scaffold` with an add
 * `FAB` slot; the glass top bar samples the page content recorded by
 * the inner `Box`. The body receives its top inset from the scaffold
 * inner padding so content never hides under the bar. Dialogs, the
 * drag-reorder helper and the snackbar host stay at entry level.
 */
@Composable
internal fun SenderListScreenMaterial(
    title: String,
    onAddClick: () -> Unit,
    fabContentDescription: String,
    listState: LazyListState,
    body: @Composable (PaddingValues) -> Unit
) {
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    val scrollScope = rememberCoroutineScope()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box {
                AppTopBar(
                    modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                    containerColor = if (glassOn) Color.Transparent else chromeSurfaceColor(),
                    title = title,
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
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = onAddClick,
                imageVector = Icons.Filled.Add,
                contentDescription = fabContentDescription,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 56.dp),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (glassOn) topGlass.contentRecorder() else Modifier),
        ) {
            body(PaddingValues(top = innerPadding.calculateTopPadding()))
            ScrollToTopFAB(
                listState = listState,
                extraBottomPadding = 96.dp,
            )
        }
    }
}
