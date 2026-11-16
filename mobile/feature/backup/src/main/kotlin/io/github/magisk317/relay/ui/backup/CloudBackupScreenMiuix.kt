package io.github.magisk317.relay.ui.backup

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.uikit.preview.MagiskMultiPreview
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/**
 * Miuix chrome for the cloud-backup screen: static top bar hosted in a
 * page-owned miuix `Scaffold` with horizontal-only content window insets and
 * no snackbar slot (the page shows messages through the ambient
 * `LocalSnackbarHostState`). The shared backup panel owns its own scrolling,
 * so this chrome carries no scroll affordances. The glass top bar samples the
 * page content recorded by the inner `Box`.
 */
@Composable
internal fun CloudBackupScreenMiuix(
    title: String,
    onBack: () -> Unit,
    body: @Composable (PaddingValues) -> Unit,
) {
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    MiuixScaffold(
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
                    windowInsets = WindowInsets.statusBars,
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
        }
    }
}

@MagiskMultiPreview
@Composable
private fun CloudBackupScreenMiuixPreview() {
    CloudBackupScreenMiuix(
        title = "Sample",
        onBack = {},
        body = { _ -> MiuixText("Preview") },
    )
}
