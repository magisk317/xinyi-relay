package io.github.magisk317.relay.ui.sender

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.contract.model.ForwardCommonConfig
import io.github.magisk317.relay.contract.settings.SimRemarkSettingsSnapshot
import io.github.magisk317.relay.core.R
import kotlinx.coroutines.delay

private const val DIALOG_WIDTH_FRACTION = 0.92f

@Composable
internal fun ForwardCommonConfigDialog(
    currentConfig: ForwardCommonConfig,
    simRemarkSettings: SimRemarkSettingsSnapshot,
    smsCodeEnabled: Boolean,
    smsPlainEnabled: Boolean,
    onSmsCodeToggle: (Boolean) -> Unit,
    onSmsPlainToggle: (Boolean) -> Unit,
    forwardSmsCodeEnabled: Boolean,
    forwardSmsPlainEnabled: Boolean,
    onForwardSmsCodeToggle: (Boolean) -> Unit,
    onForwardSmsPlainToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ForwardCommonConfig) -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val fillTemplateInteractionSource = remember { MutableInteractionSource() }
    val isFillTemplatePressed by fillTemplateInteractionSource.collectIsPressedAsState()
    var suppressNextClick by remember { mutableStateOf(false) }
    val templateState = rememberSenderTemplateEditorState(
        currentConfig.messageTemplate,
        currentConfig.deviceName,
    ) { templateText ->
        val previewConfig = currentConfig.copy(messageTemplate = templateText)
        ForwardCommonConfigStore.applyToMessage(
            context = context,
            messageType = MessageType.SMS_PLAIN,
            msgInfo = buildSmsPreviewMessage(context),
            config = previewConfig,
            simRemarkSnapshot = simRemarkSettings,
        ).content
    }

    @Suppress("MagicNumber")
    LaunchedEffect(isFillTemplatePressed) {
        if (isFillTemplatePressed) {
            delay(10_000)
            if (isFillTemplatePressed) {
                suppressNextClick = true
                val fullTemplate = ForwardCommonConfigStore.fullInfoTemplate()
                templateState.replaceTemplate(fullTemplate)
            }
        }
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_FRACTION),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sender_sms_config_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.sender_gate_ingress_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.sender_gate_ingress_summary_sms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_msg_type_sms_code_title),
                    summary = stringResource(id = R.string.pref_msg_type_sms_code_summary),
                    checked = smsCodeEnabled,
                    onCheckedChange = onSmsCodeToggle,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_msg_type_sms_plain_title),
                    summary = stringResource(id = R.string.pref_msg_type_sms_plain_summary),
                    checked = smsPlainEnabled,
                    onCheckedChange = onSmsPlainToggle,
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.sender_gate_forwarding_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_forward_sms_code_title),
                    summary = stringResource(id = R.string.pref_forward_sms_code_summary),
                    checked = forwardSmsCodeEnabled,
                    onCheckedChange = onForwardSmsCodeToggle,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_forward_sms_plain_title),
                    summary = stringResource(id = R.string.pref_forward_sms_plain_summary),
                    checked = forwardSmsPlainEnabled,
                    onCheckedChange = onForwardSmsPlainToggle,
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = templateState.value,
                    onValueChange = templateState::onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .onFocusChanged { focusState ->
                            templateState.onFocusChanged(focusState.isFocused)
                        },
                    label = { Text(stringResource(R.string.sender_template_sms_label)) },
                    placeholder = { Text(stringResource(R.string.sender_template_placeholder)) },
                    supportingText = { Text(stringResource(R.string.sender_template_supporting)) },
                )
                Text(
                    text = stringResource(R.string.sender_template_preview, templateState.preview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            if (suppressNextClick) {
                                suppressNextClick = false
                                return@TextButton
                            }
                            val defaultTemplate = ForwardCommonConfigStore.defaultTemplate()
                            templateState.replaceTemplate(defaultTemplate)
                        },
                        interactionSource = fillTemplateInteractionSource,
                    ) {
                        Text(stringResource(R.string.sender_template_fill_default))
                    }
                }
                HorizontalDivider()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(forwardTemplateVariables.size) { index ->
                        val variable = forwardTemplateVariables[index]
                        OutlinedButton(
                            onClick = { templateState.insertToken(variable.token) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(stringResource(variable.labelRes), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        currentConfig.copy(
                            messageTemplate = templateState.value.text,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
internal fun AppNotifyTemplateDialog(
    currentTemplate: String,
    currentCommonConfig: ForwardCommonConfig,
    simRemarkSettings: SimRemarkSettingsSnapshot,
    appNotifyEnabled: Boolean,
    onAppNotifyToggle: (Boolean) -> Unit,
    forwardAppNotifyEnabled: Boolean,
    onForwardAppNotifyToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val fillTemplateInteractionSource = remember { MutableInteractionSource() }
    val isFillTemplatePressed by fillTemplateInteractionSource.collectIsPressedAsState()
    var suppressNextClick by remember { mutableStateOf(false) }
    val templateState = rememberSenderTemplateEditorState(
        currentTemplate,
        currentCommonConfig.deviceName,
    ) { templateText ->
        val previewConfig = currentCommonConfig.copy(messageTemplate = templateText)
        ForwardCommonConfigStore.applyToMessage(
            context = context,
            messageType = MessageType.APP_NOTIFY,
            msgInfo = buildAppNotifyPreviewMessage(context),
            config = previewConfig,
            simRemarkSnapshot = simRemarkSettings,
        ).content
    }

    @Suppress("MagicNumber")
    LaunchedEffect(isFillTemplatePressed) {
        if (isFillTemplatePressed) {
            delay(10_000)
            if (isFillTemplatePressed) {
                suppressNextClick = true
                val fullTemplate = appNotifyFullTemplate()
                templateState.replaceTemplate(fullTemplate)
            }
        }
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_FRACTION),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sender_app_config_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.sender_gate_ingress_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.sender_gate_ingress_summary_event),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_msg_type_app_notify_title),
                    summary = stringResource(id = R.string.pref_msg_type_app_notify_summary),
                    checked = appNotifyEnabled,
                    onCheckedChange = onAppNotifyToggle,
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.sender_gate_forwarding_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_forward_app_notify_title),
                    summary = stringResource(id = R.string.pref_forward_app_notify_summary),
                    checked = forwardAppNotifyEnabled,
                    onCheckedChange = onForwardAppNotifyToggle,
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = templateState.value,
                    onValueChange = templateState::onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .onFocusChanged { focusState ->
                            templateState.onFocusChanged(focusState.isFocused)
                        },
                    label = { Text(stringResource(R.string.sender_template_app_label)) },
                    placeholder = { Text(stringResource(R.string.sender_template_placeholder)) },
                    supportingText = { Text(stringResource(R.string.sender_template_supporting)) },
                )
                Text(
                    text = stringResource(R.string.sender_template_preview, templateState.preview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            if (suppressNextClick) {
                                suppressNextClick = false
                                return@TextButton
                            }
                            val defaultTemplate = appNotifyDefaultTemplate()
                            templateState.replaceTemplate(defaultTemplate)
                        },
                        interactionSource = fillTemplateInteractionSource,
                    ) {
                        Text(stringResource(R.string.sender_template_fill_default))
                    }
                }
                HorizontalDivider()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(appNotifyTemplateVariables.size) { index ->
                        val variable = appNotifyTemplateVariables[index]
                        OutlinedButton(
                            onClick = { templateState.insertToken(variable.token) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(stringResource(variable.labelRes), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(templateState.value.text) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
internal fun CallNotifyTemplateDialog(
    currentTemplate: String,
    currentCommonConfig: ForwardCommonConfig,
    simRemarkSettings: SimRemarkSettingsSnapshot,
    callNotifyEnabled: Boolean,
    onCallNotifyToggle: (Boolean) -> Unit,
    forwardCallNotifyEnabled: Boolean,
    onForwardCallNotifyToggle: (Boolean) -> Unit,
    forwardCallNotifyFinalEnabled: Boolean,
    onForwardCallNotifyFinalToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val fillTemplateInteractionSource = remember { MutableInteractionSource() }
    val isFillTemplatePressed by fillTemplateInteractionSource.collectIsPressedAsState()
    var suppressNextClick by remember { mutableStateOf(false) }
    val templateState = rememberSenderTemplateEditorState(
        currentTemplate,
        currentCommonConfig.deviceName,
    ) { templateText ->
        val previewConfig = currentCommonConfig.copy(messageTemplate = templateText)
        ForwardCommonConfigStore.applyToMessage(
            context = context,
            messageType = MessageType.CALL_NOTIFY,
            msgInfo = buildCallNotifyPreviewMessage(context),
            config = previewConfig,
            simRemarkSnapshot = simRemarkSettings,
        ).content
    }

    @Suppress("MagicNumber")
    LaunchedEffect(isFillTemplatePressed) {
        if (isFillTemplatePressed) {
            delay(10_000)
            if (isFillTemplatePressed) {
                suppressNextClick = true
                val fullTemplate = callNotifyFullTemplate()
                templateState.replaceTemplate(fullTemplate)
            }
        }
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_FRACTION),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sender_call_config_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.sender_gate_ingress_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.sender_gate_ingress_summary_event),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_msg_type_call_notify_title),
                    summary = stringResource(id = R.string.pref_msg_type_call_notify_summary),
                    checked = callNotifyEnabled,
                    onCheckedChange = onCallNotifyToggle,
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.sender_gate_forwarding_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_forward_call_notify_title),
                    summary = stringResource(id = R.string.pref_forward_call_notify_summary),
                    checked = forwardCallNotifyEnabled,
                    onCheckedChange = onForwardCallNotifyToggle,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_forward_call_notify_final_title),
                    summary = stringResource(id = R.string.pref_forward_call_notify_final_summary),
                    checked = forwardCallNotifyFinalEnabled,
                    onCheckedChange = onForwardCallNotifyFinalToggle,
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = templateState.value,
                    onValueChange = templateState::onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .onFocusChanged { focusState ->
                            templateState.onFocusChanged(focusState.isFocused)
                        },
                    label = { Text(stringResource(R.string.sender_template_call_label)) },
                    placeholder = { Text(stringResource(R.string.sender_template_placeholder)) },
                    supportingText = { Text(stringResource(R.string.sender_template_supporting)) },
                )
                Text(
                    text = stringResource(R.string.sender_template_preview, templateState.preview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            if (suppressNextClick) {
                                suppressNextClick = false
                                return@TextButton
                            }
                            val defaultTemplate = callNotifyDefaultTemplate()
                            templateState.replaceTemplate(defaultTemplate)
                        },
                        interactionSource = fillTemplateInteractionSource,
                    ) {
                        Text(stringResource(R.string.sender_template_fill_default))
                    }
                }
                HorizontalDivider()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(callNotifyTemplateVariables.size) { index ->
                        val variable = callNotifyTemplateVariables[index]
                        OutlinedButton(
                            onClick = { templateState.insertToken(variable.token) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(stringResource(variable.labelRes), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(templateState.value.text) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
