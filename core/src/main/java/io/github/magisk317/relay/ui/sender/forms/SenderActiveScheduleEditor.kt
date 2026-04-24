package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.domain.sender.SenderActiveSchedule
import io.github.magisk317.relay.domain.sender.SenderActiveScheduleConst
import io.github.magisk317.relay.domain.sender.SenderActiveScheduleEvaluator
import io.github.magisk317.relay.domain.sender.SenderActiveScheduleRange
import io.github.magisk317.relay.domain.sender.SenderActiveScheduleRule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

enum class SenderScheduleSection {
    SMS,
    APP_NOTIFY,
    CALL_NOTIFY,
}

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
@OptIn(ExperimentalLayoutApi::class)
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SenderScheduleSection.entries.forEach { candidate ->
                        FilterChip(
                            selected = section == candidate,
                            onClick = { section = candidate },
                            label = {
                                Text(
                                    text = when (candidate) {
                                        SenderScheduleSection.SMS -> stringResource(R.string.sender_active_schedule_sms)
                                        SenderScheduleSection.APP_NOTIFY -> stringResource(R.string.sender_active_schedule_app_notify)
                                        SenderScheduleSection.CALL_NOTIFY -> stringResource(R.string.sender_active_schedule_call_notify)
                                    },
                                )
                            },
                        )
                    }
                }

                SenderActiveScheduleRuleEditor(
                    rule = draft.ruleFor(section),
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
@OptIn(ExperimentalLayoutApi::class)
private fun SenderActiveScheduleRuleEditor(
    rule: SenderActiveScheduleRule,
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
                    text = stringResource(R.string.sender_active_schedule_title),
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
                                ranges = listOf(SenderActiveScheduleRange("09:00", "18:00")),
                            )
                        } else {
                            rule.copy(enabled = enabled)
                        },
                    )
                },
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = rule.mode == SenderActiveScheduleConst.MODE_BLACKLIST,
                onClick = {
                    onRuleChange(rule.copy(mode = SenderActiveScheduleConst.MODE_BLACKLIST))
                },
                label = { Text(stringResource(R.string.sender_active_schedule_mode_blacklist)) },
            )
            FilterChip(
                selected = rule.mode == SenderActiveScheduleConst.MODE_WHITELIST,
                onClick = {
                    onRuleChange(rule.copy(mode = SenderActiveScheduleConst.MODE_WHITELIST))
                },
                label = { Text(stringResource(R.string.sender_active_schedule_mode_whitelist)) },
            )
        }

        Text(
            text = stringResource(R.string.sender_active_schedule_weekdays_title),
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SenderActiveScheduleConst.ALL_WEEKDAYS.forEach { weekday ->
                val selected = weekday in rule.weekdays
                FilterChip(
                    selected = selected,
                    onClick = {
                        val nextWeekdays = if (selected) {
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
                    label = {
                        Text(
                            DayOfWeek.of(weekday).getDisplayName(
                                TextStyle.SHORT,
                                Locale.getDefault(),
                            ),
                        )
                    },
                )
            }
        }

        Text(
            text = stringResource(R.string.sender_active_schedule_ranges_title),
            style = MaterialTheme.typography.titleSmall,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rule.ranges.forEachIndexed { index, range ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = range.start,
                        onValueChange = { value ->
                            onRuleChange(rule.withRange(index, range.copy(start = value)))
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.sender_active_schedule_start)) },
                        placeholder = { Text("09:00") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = range.end,
                        onValueChange = { value ->
                            onRuleChange(rule.withRange(index, range.copy(end = value)))
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.sender_active_schedule_end)) },
                        placeholder = { Text("18:00") },
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            onRuleChange(rule.copy(ranges = rule.ranges.filterIndexed { i, _ -> i != index }))
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
                onRuleChange(rule.copy(ranges = rule.ranges + SenderActiveScheduleRange("09:00", "18:00")))
            },
        ) {
            Text(stringResource(R.string.sender_active_schedule_add_range))
        }
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
    if (!rule.enabled || rule.ranges.isEmpty()) {
        return stringResource(R.string.sender_active_schedule_no_restrictions)
    }
    val modeLabel = when (rule.mode) {
        SenderActiveScheduleConst.MODE_WHITELIST -> stringResource(R.string.sender_active_schedule_mode_whitelist)
        else -> stringResource(R.string.sender_active_schedule_mode_blacklist)
    }
    return "$modeLabel · ${rule.ranges.size}"
}
