package io.github.magisk317.relay.ui.home.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.preference.NonNegativeIntegerInputDialog

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
