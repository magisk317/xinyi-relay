package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.domain.sender.SenderActiveSchedule
import io.github.magisk317.relay.domain.sender.SenderActiveScheduleConst
import io.github.magisk317.relay.domain.sender.SenderActiveScheduleEvaluator
import io.github.magisk317.relay.domain.sender.SenderActiveScheduleRange
import io.github.magisk317.relay.domain.sender.SenderActiveScheduleRule
import io.github.magisk317.relay.ui.common.CenteredChipText
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

enum class SenderScheduleSection {
    SMS,
    APP_NOTIFY,
    CALL_NOTIFY,
}

private val SenderScheduleStarterRange = SenderActiveScheduleRange("09:00", "18:00")

fun buildSenderActiveScheduleSummary(
    schedule: SenderActiveSchedule,
    context: android.content.Context,
): String {
    val summary = SenderActiveScheduleEvaluator.summarize(schedule)
    if (summary.smsRanges == 0 && summary.appNotifyRanges == 0 && summary.callNotifyRanges == 0) {
        return context.getString(R.string.sender_active_schedule_no_restrictions)
    }
    return context.getString(
        R.string.sender_active_schedule_summary_format,
        summary.smsRanges,
        summary.appNotifyRanges,
        summary.callNotifyRanges,
    )
}

@Composable
fun SenderActiveScheduleDialog(
    schedule: SenderActiveSchedule,
    onDismiss: () -> Unit,
    onConfirm: (SenderActiveSchedule) -> Unit,
) {
    var draft by remember(schedule) { mutableStateOf(SenderActiveScheduleEvaluator.sanitize(schedule)) }
    var section by remember { mutableStateOf(SenderScheduleSection.SMS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sender_active_schedule_editor_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SenderActiveScheduleRuleEditor(
                    section = section,
                    rule = draft.ruleFor(section),
                    onSectionChange = { section = it },
                    onRuleChange = { nextRule ->
                        draft = draft.withRule(section, nextRule)
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(SenderActiveScheduleEvaluator.sanitize(draft)) }) {
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
private fun SenderActiveScheduleRuleEditor(
    section: SenderScheduleSection,
    rule: SenderActiveScheduleRule,
    onSectionChange: (SenderScheduleSection) -> Unit,
    onRuleChange: (SenderActiveScheduleRule) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sender_active_schedule_switch_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = buildRuleSummary(rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = { enabled ->
                    onRuleChange(
                        if (enabled && rule.ranges.isEmpty()) {
                            rule.copy(
                                enabled = true,
                                weekdays = if (rule.weekdays.isEmpty()) SenderActiveScheduleConst.ALL_WEEKDAYS else rule.weekdays,
                                ranges = listOf(SenderScheduleStarterRange),
                            )
                        } else {
                            rule.copy(enabled = enabled)
                        },
                    )
                },
            )
        }

        SingleChoiceSegmentedSelector(
            options = listOf(
                SegmentedOption(SenderScheduleSection.SMS, stringResource(R.string.sender_active_schedule_sms)),
                SegmentedOption(SenderScheduleSection.APP_NOTIFY, stringResource(R.string.sender_active_schedule_app_notify)),
                SegmentedOption(SenderScheduleSection.CALL_NOTIFY, stringResource(R.string.sender_active_schedule_call_notify)),
            ),
            selected = section,
            onSelect = onSectionChange,
        )

        SingleChoiceSegmentedSelector(
            options = listOf(
                SegmentedOption(SenderActiveScheduleConst.MODE_BLACKLIST, stringResource(R.string.sender_active_schedule_mode_blacklist)),
                SegmentedOption(SenderActiveScheduleConst.MODE_WHITELIST, stringResource(R.string.sender_active_schedule_mode_whitelist)),
            ),
            selected = rule.mode,
            onSelect = { nextMode -> onRuleChange(rule.copy(mode = nextMode)) },
        )

        Text(
            text = stringResource(R.string.sender_active_schedule_weekdays_title),
            style = MaterialTheme.typography.titleSmall,
        )
        WeekdayRow(
            weekdays = listOf(1, 2, 3, 4),
            selectedWeekdays = rule.weekdays,
            onWeekdayToggle = { weekday ->
                val nextWeekdays = if (weekday in rule.weekdays) {
                    rule.weekdays.filterNot { it == weekday }
                } else {
                    (rule.weekdays + weekday).distinct().sorted()
                }
                onRuleChange(
                    rule.copy(
                        weekdays = nextWeekdays.ifEmpty { SenderActiveScheduleConst.ALL_WEEKDAYS },
                    ),
                )
            },
        )
        WeekdayRow(
            weekdays = SENDER_ACTIVE_SCHEDULE_SECOND_WEEKDAY_ROW,
            selectedWeekdays = rule.weekdays,
            onWeekdayToggle = { weekday ->
                val nextWeekdays = if (weekday in rule.weekdays) {
                    rule.weekdays.filterNot { it == weekday }
                } else {
                    (rule.weekdays + weekday).distinct().sorted()
                }
                onRuleChange(
                    rule.copy(
                        weekdays = nextWeekdays.ifEmpty { SenderActiveScheduleConst.ALL_WEEKDAYS },
                    ),
                )
            },
        )

        Text(
            text = stringResource(R.string.sender_active_schedule_ranges_title),
            style = MaterialTheme.typography.titleSmall,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rule.ranges.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CenteredChipText(
                        text = stringResource(R.string.sender_active_schedule_start),
                        modifier = Modifier.weight(1f),
                    )
                    CenteredChipText(
                        text = stringResource(R.string.sender_active_schedule_end),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.weight(SENDER_ACTIVE_SCHEDULE_ACTION_WEIGHT))
                }
            }
            rule.ranges.forEachIndexed { index, range ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeValueButton(
                        modifier = Modifier.weight(1f),
                        value = range.start,
                        onValueChange = { value ->
                            onRuleChange(rule.withRange(index, range.copy(start = value)))
                        },
                    )
                    TimeValueButton(
                        modifier = Modifier.weight(1f),
                        value = range.end,
                        onValueChange = { value ->
                            onRuleChange(rule.withRange(index, range.copy(end = value)))
                        },
                    )
                    IconButton(
                        modifier = Modifier.weight(SENDER_ACTIVE_SCHEDULE_ACTION_WEIGHT),
                        onClick = {
                            val nextRanges = rule.ranges.filterIndexed { i, _ -> i != index }
                            onRuleChange(
                                rule.copy(
                                    enabled = rule.enabled && nextRanges.isNotEmpty(),
                                    ranges = nextRanges,
                                ),
                            )
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.sender_active_schedule_remove_range),
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                onRuleChange(rule.copy(ranges = rule.ranges + SenderScheduleStarterRange))
            },
        ) {
            Text(stringResource(R.string.sender_active_schedule_add_range))
        }
    }
}

private val SENDER_ACTIVE_SCHEDULE_SECOND_WEEKDAY_ROW = listOf(5, 6, 7)
private const val SENDER_ACTIVE_SCHEDULE_ACTION_WEIGHT = 0.2f

@Composable
private fun WeekdayRow(
    weekdays: List<Int>,
    selectedWeekdays: List<Int>,
    onWeekdayToggle: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        weekdays.forEach { weekday ->
            FilterChip(
                selected = weekday in selectedWeekdays,
                onClick = { onWeekdayToggle(weekday) },
                label = {
                    CenteredChipText(
                        text = DayOfWeek.of(weekday).getDisplayName(
                            TextStyle.SHORT,
                            Locale.getDefault(),
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TimeValueButton(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    FilledTonalButton(
        onClick = { showPicker = true },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
    if (showPicker) {
        TimeRangePickerDialog(
            initialValue = value,
            onDismiss = { showPicker = false },
            onConfirm = { selected ->
                onValueChange(selected)
                showPicker = false
            },
        )
    }
}

private fun SenderActiveSchedule.ruleFor(section: SenderScheduleSection): SenderActiveScheduleRule {
    return when (section) {
        SenderScheduleSection.SMS -> sms
        SenderScheduleSection.APP_NOTIFY -> appNotify
        SenderScheduleSection.CALL_NOTIFY -> callNotify
    }
}

private fun SenderActiveSchedule.withRule(
    section: SenderScheduleSection,
    rule: SenderActiveScheduleRule,
): SenderActiveSchedule {
    return when (section) {
        SenderScheduleSection.SMS -> copy(sms = rule)
        SenderScheduleSection.APP_NOTIFY -> copy(appNotify = rule)
        SenderScheduleSection.CALL_NOTIFY -> copy(callNotify = rule)
    }
}

private fun SenderActiveScheduleRule.withRange(
    index: Int,
    range: SenderActiveScheduleRange,
): SenderActiveScheduleRule {
    return copy(
        ranges = ranges.mapIndexed { currentIndex, currentRange ->
            if (currentIndex == index) range else currentRange
        },
    )
}

@Composable
private fun buildRuleSummary(rule: SenderActiveScheduleRule): String {
    if (!rule.enabled) {
        return stringResource(R.string.sender_active_schedule_disabled_summary)
    }
    val modeLabel = when (rule.mode) {
        SenderActiveScheduleConst.MODE_WHITELIST -> stringResource(R.string.sender_active_schedule_mode_whitelist)
        else -> stringResource(R.string.sender_active_schedule_mode_blacklist)
    }
    val modeExplanation = when (rule.mode) {
        SenderActiveScheduleConst.MODE_WHITELIST -> stringResource(R.string.sender_active_schedule_mode_whitelist_explanation)
        else -> stringResource(R.string.sender_active_schedule_mode_blacklist_explanation)
    }
    return "$modeLabel · $modeExplanation"
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TimeRangePickerDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val totalMinutes = SenderActiveScheduleConst.parseMinutes(initialValue) ?: (9 * 60)
    val initialHour = totalMinutes / 60
    val initialMinute = totalMinutes % 60
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sender_active_schedule_pick_time)) },
        text = {
            TimePicker(state = pickerState)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        String.format(
                            Locale.US,
                            "%02d:%02d",
                            pickerState.hour,
                            pickerState.minute,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
