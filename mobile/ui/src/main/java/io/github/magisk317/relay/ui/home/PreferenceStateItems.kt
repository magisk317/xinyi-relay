@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.home

import io.github.magisk317.relay.ui.common.showLatestSnackbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.android.prefs.HookPreferenceMirror
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import io.github.magisk317.relay.ui.common.SingleChoiceOptionDialog
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
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier,
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    supportingText: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    resetValue: String? = null,
    validator: ((String) -> String?)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    inputFilter: ((String) -> String)? = null,
    onFocusLost: ((String) -> Unit)? = null,
    onConfirm: (String) -> Unit,
) {
    var fieldValue by remember(title, initialValue) { mutableStateOf(TextFieldValue(initialValue)) }
    val errorText = validator?.invoke(fieldValue.text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { updated ->
                        val filteredText = inputFilter?.invoke(updated.text) ?: updated.text
                        fieldValue = if (filteredText == updated.text) {
                            updated
                        } else {
                            updated.copy(
                                text = filteredText,
                                selection = TextRange(filteredText.length),
                            )
                        }
                    },
                    singleLine = singleLine,
                    maxLines = maxLines,
                    keyboardOptions = keyboardOptions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused && errorText == null) {
                                onFocusLost?.invoke(fieldValue.text)
                            }
                        },
                    supportingText = {
                        when {
                            errorText != null -> Text(errorText)
                            !supportingText.isNullOrBlank() -> Text(supportingText)
                        }
                    },
                    isError = errorText != null,
                )
                if (resetValue != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        fieldValue = TextFieldValue(
                            text = resetValue,
                            selection = TextRange(resetValue.length),
                        )
                    }) {
                        Text(text = stringResource(id = R.string.action_restore_default))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(fieldValue.text) },
                enabled = errorText == null,
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
        title = { Text(text = stringResource(id = R.string.pref_privacy_policy_title)) },
        text = { Text(text = stringResource(id = R.string.privacy_policy_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(id = R.string.action_accept)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onViewPolicy) { Text(text = stringResource(id = R.string.action_view_details)) }
                TextButton(onClick = onCancel) { Text(text = stringResource(id = R.string.action_decline)) }
            }
        },
    )
}

@Composable
fun DonateDialog(
    onDismiss: () -> Unit,
    onAlipay: () -> Unit,
    onWechat: () -> Unit,
    showPlayDonations: Boolean = false,
    onDonate099: () -> Unit = {},
    onDonate200: () -> Unit = {},
    onDonate999: () -> Unit = {},
    onDonate1999: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.dialog_donate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = stringResource(id = R.string.dialog_donate_summary))
                if (showPlayDonations) {
                    Text(
                        text = stringResource(id = R.string.donate_one_time_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = onDonate099, modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(id = R.string.donate_one_time_099))
                        }
                        Button(onClick = onDonate200, modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(id = R.string.donate_one_time_200))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = onDonate999, modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(id = R.string.donate_one_time_999))
                        }
                        Button(onClick = onDonate1999, modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(id = R.string.donate_one_time_1999))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAlipay) {
                Text(text = stringResource(id = R.string.dialog_donate_alipay))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onWechat) { Text(text = stringResource(id = R.string.dialog_donate_wechat)) }
                TextButton(onClick = onDismiss) { Text(text = stringResource(id = R.string.cancel)) }
            }
        },
    )
}

@Composable
fun QRCodeDialog(
    resId: Int,
    type: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = type) },
        text = {
            Image(
                painter = painterResource(id = resId),
                contentDescription = type,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = onSave) { Text(text = stringResource(id = R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = stringResource(id = R.string.cancel)) } },
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
    val selectedIndex = remember(selectedValue, entriesId, valuesId) {
        values.indexOf(selectedValue).coerceAtLeast(0)
    }

    SingleChoiceOptionDialog(
        title = title,
        options = entries.toList(),
        selectedIndex = selectedIndex,
        onDismiss = onDismiss,
    ) { index ->
        onConfirm(values[index])
    }
}
