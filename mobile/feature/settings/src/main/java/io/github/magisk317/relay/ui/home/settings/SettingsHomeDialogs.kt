package io.github.magisk317.relay.ui.home.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.preference.NonNegativeIntegerInputDialog
import io.github.magisk317.uikit.preference.SingleChoiceConfirmDialog

@Composable
internal fun SettingsThemeDialog(
    selectedMode: Int,
    onDismiss: () -> Unit,
    onSelectionChange: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val themeOptions = listOf(
        stringResource(id = R.string.theme_follow_system),
        stringResource(id = R.string.theme_light),
        stringResource(id = R.string.theme_dark),
        stringResource(id = R.string.theme_black),
    )
    SingleChoiceConfirmDialog(
        title = stringResource(id = R.string.pref_choose_theme_title),
        options = themeOptions,
        selectedIndex = selectedMode.coerceIn(themeOptions.indices),
        onDismissRequest = onDismiss,
        onSelectionChange = onSelectionChange,
        onConfirm = { onConfirm(selectedMode) },
    )
}

@Composable
internal fun SettingsLanguageDialog(
    selectedTag: String,
    onDismiss: () -> Unit,
    onSelectionChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
) {
    val languageTags = listOf("", "zh-CN", "zh-TW", "en")
    val languageOptions = listOf(
        stringResource(id = R.string.language_follow_system),
        stringResource(id = R.string.language_zh_cn),
        stringResource(id = R.string.language_zh_tw),
        stringResource(id = R.string.language_en),
    )
    SingleChoiceConfirmDialog(
        title = stringResource(id = R.string.pref_language_title),
        options = languageOptions,
        selectedIndex = languageTags.indexOf(selectedTag).takeIf { it >= 0 } ?: 0,
        onDismissRequest = onDismiss,
        onSelectionChange = { index -> onSelectionChange(languageTags[index]) },
        onConfirm = { onConfirm(selectedTag) },
    )
}

@Composable
internal fun SettingsRuntimeLogRetentionDialog(
    retentionDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val runtimeLogRetentionDaysError = stringResource(id = R.string.pref_runtime_log_retention_days_error)
    NonNegativeIntegerInputDialog(
        title = stringResource(id = R.string.pref_runtime_log_retention_days_title),
        initialValue = retentionDays,
        errorText = runtimeLogRetentionDaysError,
        onDismiss = onDismiss,
        minimumValue = PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN,
        supportingText = stringResource(id = R.string.pref_runtime_log_retention_days_hint),
    ) { updated ->
        onConfirm(updated)
    }
}

@Composable
internal fun themeModeSummary(mode: Int): String {
    return when (mode) {
        1 -> stringResource(id = R.string.theme_light)
        2 -> stringResource(id = R.string.theme_dark)
        3 -> stringResource(id = R.string.theme_black)
        else -> stringResource(id = R.string.theme_follow_system)
    }
}

@Composable
internal fun languageSummary(languageTag: String): String {
    return when (languageTag) {
        "zh-CN" -> stringResource(id = R.string.language_zh_cn)
        "zh-TW" -> stringResource(id = R.string.language_zh_tw)
        "en" -> stringResource(id = R.string.language_en)
        else -> stringResource(id = R.string.language_follow_system)
    }
}
