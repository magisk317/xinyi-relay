package io.github.magisk317.relay.ui.home.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.surface.PageScaffoldMiuix
import io.github.magisk317.uikit.preview.MagiskMultiPreview
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/** Miuix chrome for [ThemeSettingsPage]. */
@Composable
internal fun ThemeSettingsMiuix(
    onBack: () -> Unit,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    PageScaffoldMiuix(
        title = stringResource(id = R.string.pref_theme_settings_title),
        onBack = onBack,
        content = body,
    )
}


@MagiskMultiPreview
@Composable
private fun ThemeSettingsMiuixPreview() {
    ThemeSettingsMiuix(
        onBack = {},
        body = { _, _ -> MiuixText("Preview") },
    )
}
