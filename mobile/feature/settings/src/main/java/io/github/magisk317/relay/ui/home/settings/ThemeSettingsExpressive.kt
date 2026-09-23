package io.github.magisk317.relay.ui.home.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.surface.PageScaffoldExpressive

/** Expressive/Material chrome for [ThemeSettingsPage]. */
@Composable
internal fun ThemeSettingsExpressive(
    onBack: () -> Unit,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    PageScaffoldExpressive(
        title = stringResource(id = R.string.pref_theme_settings_title),
        onBack = onBack,
        content = body,
    )
}
