package io.github.magisk317.relay.ui.sender

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.contract.constant.DispatchStrategy
import io.github.magisk317.relay.contract.model.ForwardCommonConfig
import io.github.magisk317.relay.contract.model.ForwardSilentPeriodConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.schedule.ForwardSilentPeriodEvaluator
import io.github.magisk317.relay.ui.common.ActiveScheduleTimeValueButton
import io.github.magisk317.relay.ui.common.ActiveScheduleWeekdayRow
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import io.github.magisk317.relay.ui.common.filterNonNegativeIntegerInput
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.AppTextField
import io.github.magisk317.uikit.surface.rememberSaveableTextFieldState
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

@Composable
internal fun GeneralConfigDialog(
    currentConfig: ForwardCommonConfig,
    currentSimSlot1Remark: String,
    currentSimSlot2Remark: String,
    onDismiss: () -> Unit,
    onSave: (ForwardCommonConfig, String, String) -> Unit,
) {
    val deviceNameState = rememberSaveableTextFieldState(currentConfig.deviceName)
    var dispatchStrategy by remember(currentConfig.dispatchStrategy) {
        mutableIntStateOf(normalizeDispatchStrategy(currentConfig.dispatchStrategy))
    }
    val simSlot1RemarkState = rememberSaveableTextFieldState(currentSimSlot1Remark)
    val simSlot2RemarkState = rememberSaveableTextFieldState(currentSimSlot2Remark)
    var silentPeriod by remember(currentConfig.silentPeriod) {
        mutableStateOf(ForwardSilentPeriodEvaluator.sanitize(currentConfig.silentPeriod))
    }
    fun toggleSilentWeekday(weekday: Int) {
        val nextWeekdays = if (weekday in silentPeriod.weekdays) {
            silentPeriod.weekdays.filterNot { it == weekday }
        } else {
            (silentPeriod.weekdays + weekday).distinct().sorted()
        }
        silentPeriod = silentPeriod.copy(
            weekdays = nextWeekdays.ifEmpty { ForwardSilentPeriodConfig.ALL_WEEKDAYS },
        )
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(text = stringResource(R.string.sender_general_config_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTextField(
                    state = deviceNameState,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.sender_dialog_device_name_label),
                    placeholderText = stringResource(R.string.sender_dialog_device_name_placeholder),
                    singleLine = true,
                )
                AppText(
                    text = stringResource(R.string.dispatch_strategy),
                    role = AppTextRole.Subtitle,
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
                HorizontalDivider()
                ConfigGateToggle(
                    title = stringResource(R.string.forward_silent_period_title),
                    summary = stringResource(R.string.forward_silent_period_summary),
                    checked = silentPeriod.enabled,
                    onCheckedChange = { enabled ->
                        silentPeriod = silentPeriod.copy(enabled = enabled)
                    },
                )
                if (silentPeriod.enabled) {
                    AppText(
                        text = stringResource(R.string.forward_silent_period_weekdays),
                        role = AppTextRole.Subtitle,
                    )
                    ActiveScheduleWeekdayRow(
                        weekdays = listOf(1, 2, 3, 4),
                        selectedWeekdays = silentPeriod.weekdays,
                        onWeekdayToggle = ::toggleSilentWeekday,
                    )
                    ActiveScheduleWeekdayRow(
                        weekdays = GENERAL_CONFIG_SECOND_WEEKDAY_ROW,
                        selectedWeekdays = silentPeriod.weekdays,
                        onWeekdayToggle = ::toggleSilentWeekday,
                    )
                    AppText(
                        text = stringResource(R.string.forward_silent_period_range),
                        role = AppTextRole.Subtitle,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ActiveScheduleTimeValueButton(
                            modifier = Modifier.weight(1f),
                            value = silentPeriod.start,
                            onValueChange = { value ->
                                silentPeriod = silentPeriod.copy(start = value)
                            },
                        )
                        ActiveScheduleTimeValueButton(
                            modifier = Modifier.weight(1f),
                            value = silentPeriod.end,
                            onValueChange = { value ->
                                silentPeriod = silentPeriod.copy(end = value)
                            },
                        )
                    }
                }
                HorizontalDivider()
                AppTextField(
                    state = simSlot1RemarkState,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.sender_dialog_sim1_note_label),
                    placeholderText = stringResource(R.string.sender_dialog_sim_note_placeholder),
                    singleLine = true,
                )
                AppTextField(
                    state = simSlot2RemarkState,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.sender_dialog_sim2_note_label),
                    placeholderText = stringResource(R.string.sender_dialog_sim_note_placeholder),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            AppTextButton(
                text = stringResource(R.string.save),
                onClick = {
                    onSave(
                        currentConfig.copy(
                            deviceName = deviceNameState.text.toString().trim(),
                            dispatchStrategy = dispatchStrategy,
                            silentPeriod = ForwardSilentPeriodEvaluator.sanitize(silentPeriod),
                        ),
                        simSlot1RemarkState.text.toString().trim(),
                        simSlot2RemarkState.text.toString().trim(),
                    )
                },
            )
        },
        dismissButton = {
            AppTextButton(text = stringResource(R.string.cancel), onClick = onDismiss)
        },
    )
}

private val GENERAL_CONFIG_SECOND_WEEKDAY_ROW = listOf(5, 6, 7)

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
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppText(
                text = stringResource(
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
            AppTextButton(
                text = stringResource(R.string.save),
                enabled = validPriority,
                onClick = {
                    onSave((parsedPriority ?: currentPriority).coerceIn(0, maxPriority))
                },
            )
        },
        dismissButton = {
            AppTextButton(text = stringResource(R.string.cancel), onClick = onDismiss)
        },
    )
}

@Composable
fun SenderCustomTemplateDialog(
    template: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var templateValue by remember { mutableStateOf(TextFieldValue(template)) }

    fun insertToken(token: String) {
        val start = templateValue.selection.start.coerceIn(0, templateValue.text.length)
        val end = templateValue.selection.end.coerceIn(0, templateValue.text.length)
        val newText = buildString {
            append(templateValue.text.substring(0, start))
            append(token)
            append(templateValue.text.substring(end))
        }
        val cursor = start + token.length
        templateValue = templateValue.copy(text = newText, selection = TextRange(cursor))
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(text = stringResource(io.github.magisk317.relay.core.R.string.sender_custom_template_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = templateValue,
                    onValueChange = { templateValue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    label = { Text(stringResource(io.github.magisk317.relay.core.R.string.sender_custom_template_label)) },
                    placeholder = { Text(stringResource(io.github.magisk317.relay.core.R.string.sender_custom_template_placeholder)) },
                )
                
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
                AppSecondaryButton(
                    onClick = { insertToken(variable.token) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    AppText(
                        text = stringResource(variable.labelRes),
                        role = AppTextRole.Footnote,
                    )
                }
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(
                text = stringResource(io.github.magisk317.relay.core.R.string.save),
                onClick = { onSave(templateValue.text) },
            )
        },
        dismissButton = {
            AppTextButton(
                text = stringResource(io.github.magisk317.relay.core.R.string.cancel),
                onClick = onDismiss,
            )
        },
    )
}
