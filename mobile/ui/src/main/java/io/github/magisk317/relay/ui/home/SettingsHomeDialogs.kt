package io.github.magisk317.relay.ui.home

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.common.filterNonNegativeIntegerInput
import io.github.magisk317.relay.ui.common.parseIntAtLeastInput

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
    SingleChoiceDialog(
        title = stringResource(id = R.string.pref_choose_theme_title),
        options = themeOptions,
        selectedIndex = selectedMode.coerceIn(themeOptions.indices),
        onDismiss = onDismiss,
        onSelectionChange = onSelectionChange,
        onConfirm = onConfirm,
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
    SingleChoiceDialog(
        title = stringResource(id = R.string.pref_language_title),
        options = languageOptions,
        selectedIndex = languageTags.indexOf(selectedTag).takeIf { it >= 0 } ?: 0,
        onDismiss = onDismiss,
        onSelectionChange = { index -> onSelectionChange(languageTags[index]) },
        onConfirm = { index -> onConfirm(languageTags[index]) },
    )
}

@Composable
internal fun SettingsRuntimeLogRetentionDialog(
    retentionDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val runtimeLogRetentionDaysError = stringResource(id = R.string.pref_runtime_log_retention_days_error)
    TextInputDialog(
        title = stringResource(id = R.string.pref_runtime_log_retention_days_title),
        initialValue = retentionDays.toString(),
        onDismiss = onDismiss,
        supportingText = stringResource(id = R.string.pref_runtime_log_retention_days_hint),
        validator = {
            if (parseIntAtLeastInput(it, PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN) != null) {
                null
            } else {
                runtimeLogRetentionDaysError
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        inputFilter = ::filterNonNegativeIntegerInput,
    ) { updated ->
        onConfirm(
            parseIntAtLeastInput(updated, PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN)
                ?: PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN,
        )
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
