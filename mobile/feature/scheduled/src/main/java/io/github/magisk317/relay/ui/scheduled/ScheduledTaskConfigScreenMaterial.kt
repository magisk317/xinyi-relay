@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.relay.ui.scheduled

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.stringResource
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHostState
import io.github.magisk317.uikit.common.DismissibleSnackbarHost
import androidx.compose.material.icons.filled.Check

/**
 * Expressive/Material chrome for the scheduled-task config screen
 * of the same name: static top bar with a Check save action wired
 * to the entry-level `validateAndSave` (passed as `onSave`); the
 * dismissible snackbar host stays on the scaffold slot. The glass
 * top bar samples the page content recorded by the inner `Box`.
 */
@Composable
internal fun ScheduledTaskConfigScreenMaterial(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    snackbarHostState: SnackbarHostState,
    body: @Composable (PaddingValues) -> Unit
) {
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
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
                    AppIconButton(onClick = onSave) {
                        AppIcon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.save),
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars,
            )
        },
        snackbarHost = {
            DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
