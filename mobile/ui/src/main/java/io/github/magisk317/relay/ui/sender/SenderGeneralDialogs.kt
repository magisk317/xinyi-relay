package io.github.magisk317.relay.ui.sender

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.constant.DispatchStrategy
import io.github.magisk317.relay.contract.model.ForwardCommonConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import io.github.magisk317.relay.ui.common.filterNonNegativeIntegerInput

@Composable
internal fun GeneralConfigDialog(
    currentConfig: ForwardCommonConfig,
    currentSimSlot1Remark: String,
    currentSimSlot2Remark: String,
    onDismiss: () -> Unit,
    onSave: (ForwardCommonConfig, String, String) -> Unit,
) {
    var deviceName by remember(currentConfig.deviceName) { mutableStateOf(currentConfig.deviceName) }
    var dispatchStrategy by remember(currentConfig.dispatchStrategy) {
        mutableIntStateOf(normalizeDispatchStrategy(currentConfig.dispatchStrategy))
    }
    var simSlot1Remark by remember(currentSimSlot1Remark) { mutableStateOf(currentSimSlot1Remark) }
    var simSlot2Remark by remember(currentSimSlot2Remark) { mutableStateOf(currentSimSlot2Remark) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sender_general_config_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sender_dialog_device_name_label)) },
                    placeholder = { Text(stringResource(R.string.sender_dialog_device_name_placeholder)) },
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.dispatch_strategy),
                    style = MaterialTheme.typography.titleSmall,
                )
                SingleChoiceSegmentedSelector(
                    options = listOf(
                        SegmentedOption(
                            DispatchStrategy.PRIMARY_ONLY,
                            stringResource(R.string.dispatch_strategy_primary_only),
                        ),
                        SegmentedOption(
                            DispatchStrategy.BROADCAST_ALL,
                            stringResource(R.string.dispatch_strategy_broadcast_all),
                        ),
                        SegmentedOption(
                            DispatchStrategy.FAILOVER,
                            stringResource(R.string.dispatch_strategy_failover),
                        ),
                    ),
                    selected = dispatchStrategy,
                    onSelect = { dispatchStrategy = it },
                )
                OutlinedTextField(
                    value = simSlot1Remark,
                    onValueChange = { simSlot1Remark = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sender_dialog_sim1_note_label)) },
                    placeholder = { Text(stringResource(R.string.sender_dialog_sim_note_placeholder)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = simSlot2Remark,
                    onValueChange = { simSlot2Remark = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sender_dialog_sim2_note_label)) },
                    placeholder = { Text(stringResource(R.string.sender_dialog_sim_note_placeholder)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        currentConfig.copy(
                            deviceName = deviceName.trim(),
                            dispatchStrategy = dispatchStrategy,
                        ),
                        simSlot1Remark.trim(),
                        simSlot2Remark.trim(),
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
internal fun SenderPriorityDialog(
    sender: Sender,
    currentPriority: Int,
    maxPriority: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val context = LocalContext.current
    var priorityText by remember(sender.id, currentPriority) { mutableStateOf(currentPriority.toString()) }
    val parsedPriority = priorityText.toIntOrNull()
    val validPriority = parsedPriority != null && parsedPriority >= 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    R.string.sender_priority_dialog_title,
                    sender.name.ifBlank { getSenderTypeName(context, sender.type) },
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = priorityText,
                onValueChange = { input ->
                    priorityText = filterNonNegativeIntegerInput(input).take(3)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.sender_priority_order)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = priorityText.isNotBlank() && !validPriority,
            )
        },
        confirmButton = {
            TextButton(
                enabled = validPriority,
                onClick = {
                    onSave((parsedPriority ?: currentPriority).coerceIn(0, maxPriority))
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
