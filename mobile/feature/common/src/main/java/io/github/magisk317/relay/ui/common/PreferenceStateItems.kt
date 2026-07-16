@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.common

import io.github.magisk317.uikit.common.showLatestSnackbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import io.github.magisk317.uikit.preference.SingleChoiceValueConfirmDialog
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.ui.window.DialogProperties

@Composable
fun rememberPrefBoolean(
    key: String,
    defaultValue: Boolean,
): MutableState<Boolean> {
    val preferenceDataSource: PreferenceDataSource = koinInject()
    val state = remember(key) { mutableStateOf(defaultValue) }
    LaunchedEffect(preferenceDataSource, key, defaultValue) {
        state.value = preferenceDataSource.getBoolean(key, defaultValue)
    }
    return state
}

@Composable
fun StateSwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    io.github.magisk317.uikit.preference.StateSwitchItem(
        title = title,
        summary = summary,
        checked = checked,
        enabled = enabled,
        modifier = modifier,
        onTitleClick = onTitleClick,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
fun ActionSwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    io.github.magisk317.uikit.preference.ActionSwitchItem(
        title = title,
        summary = summary,
        checked = checked,
        enabled = enabled,
        modifier = modifier,
        onClick = onClick,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
fun Item(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    io.github.magisk317.uikit.preference.Item(
        title = title,
        summary = summary,
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun SwitchItem(
    title: String,
    summary: String,
    key: String,
    defaultValue: Boolean,
    stateOverride: MutableState<Boolean>? = null,
    onSaved: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val preferenceDataSource: PreferenceDataSource = koinInject()
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val state = stateOverride ?: rememberPrefBoolean(key, defaultValue)
    val defaultSavedSnackbar = context.getString(R.string.pref_sync_snackbar)
    StateSwitchItem(
        title = title,
        summary = summary,
        checked = state.value,
    ) { enabled ->
        state.value = enabled
        scope.launch {
            preferenceDataSource.setBoolean(key, enabled)
            HookPreferenceMirror.publish(context)
            if (onSaved != null) {
                onSaved()
            } else {
                snackbarHostState.showLatestSnackbar(defaultSavedSnackbar)
            }
        }
    }
}

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onViewPolicy: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
) {
    io.github.magisk317.uikit.surface.AppAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
        title = { Text(text = stringResource(id = R.string.pref_privacy_policy_title)) },
        text = { Text(text = stringResource(id = R.string.privacy_policy_confirm_message)) },
        confirmButton = {
            io.github.magisk317.uikit.surface.AppTextButton(
                text = stringResource(id = R.string.action_accept),
                onClick = onConfirm,
            )
        },
        dismissButton = {
            Row {
                io.github.magisk317.uikit.surface.AppTextButton(
                    text = stringResource(id = R.string.action_view_details),
                    onClick = onViewPolicy,
                )
                io.github.magisk317.uikit.surface.AppTextButton(
                    text = stringResource(id = R.string.action_decline),
                    onClick = onCancel,
                )
            }
        },
    )
}


@Composable
fun RetentionDialog(
    selectedValue: String,
    onDismiss: () -> Unit,
    titleId: Int,
    entriesId: Int,
    valuesId: Int,
    onConfirm: (String) -> Unit,
) {
    val title = stringResource(id = titleId)
    val entries = stringArrayResource(id = entriesId)
    val values = stringArrayResource(id = valuesId)
    SingleChoiceValueConfirmDialog(
        title = title,
        options = entries.toList(),
        values = values.toList(),
        selectedValue = selectedValue,
        onDismissRequest = onDismiss,
        onConfirm = onConfirm,
    )
}
