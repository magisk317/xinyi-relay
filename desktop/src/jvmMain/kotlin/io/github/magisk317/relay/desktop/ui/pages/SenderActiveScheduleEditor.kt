package io.github.magisk317.relay.desktop.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.magisk317.forward.contract.model.SenderActiveSchedule
import io.github.magisk317.forward.contract.model.SenderActiveScheduleConst
import io.github.magisk317.forward.contract.model.SenderActiveScheduleEvaluator
import io.github.magisk317.forward.contract.model.SenderActiveScheduleRange
import io.github.magisk317.forward.contract.model.SenderActiveScheduleRule
import io.github.magisk317.relay.desktop.config.SenderLocalizedText
import io.github.magisk317.relay.desktop.config.resolveSenderText
import io.github.magisk317.relay.desktop.i18n.DesktopLocale
import io.github.magisk317.relay.desktop.ui.ActionButton
import io.github.magisk317.relay.desktop.ui.ActionTone
import io.github.magisk317.relay.desktop.ui.ConsoleInk
import io.github.magisk317.relay.desktop.ui.MetricRow
import io.github.magisk317.relay.desktop.ui.RelayOption
import io.github.magisk317.relay.desktop.ui.RelaySelect
import io.github.magisk317.relay.desktop.ui.RelaySwitch
import io.github.magisk317.relay.desktop.ui.SurfaceCard

/** Text the webUI keeps inline in SenderActiveScheduleEditor. */
private object ScheduleText {
    val title = SenderLocalizedText("Active time", "生效时间", "生效時間")
    val noRestrictions = SenderLocalizedText("No schedule restrictions", "未限制时间段", "未限制時間段")
    val sms = SenderLocalizedText("SMS", "短信", "簡訊")
    val appNotify = SenderLocalizedText("App Notify", "应用通知", "應用通知")
    val callNotify = SenderLocalizedText("Call Notify", "通话通知", "通話通知")
    val blacklist = SenderLocalizedText("Blacklist", "黑名单", "黑名單")
    val whitelist = SenderLocalizedText("Whitelist", "白名单", "白名單")
    val weekdays = SenderLocalizedText("Weekdays", "星期", "星期")
    val ranges = SenderLocalizedText("Time ranges", "时间段", "時間段")
    val addRange = SenderLocalizedText("Add range", "添加时间段", "新增時間段")
    val remove = SenderLocalizedText("Remove", "删除", "刪除")
}

/** Weekday pills carry a value and two labels, like the webUI DAYS table. */
private val SCHEDULE_DAYS = listOf(
    1 to SenderLocalizedText("Mon", "周一"),
    2 to SenderLocalizedText("Tue", "周二"),
    3 to SenderLocalizedText("Wed", "周三"),
    4 to SenderLocalizedText("Thu", "周四"),
    5 to SenderLocalizedText("Fri", "周五"),
    6 to SenderLocalizedText("Sat", "周六"),
    7 to SenderLocalizedText("Sun", "周日"),
)

private enum class ScheduleBucket { SMS, APP_NOTIFY, CALL_NOTIFY }

/**
 * Active time editor ported from SenderActiveScheduleEditor.tsx: per message
 * class rules with a blacklist/whitelist mode, weekday pills and time ranges.
 */
@Composable
internal fun SenderActiveScheduleEditor(
    schedule: SenderActiveSchedule,
    locale: DesktopLocale,
    onChange: (SenderActiveSchedule) -> Unit,
) {
    val normalized = remember(schedule) { SenderActiveScheduleEvaluator.sanitize(schedule) }
    val counts = remember(normalized) { SenderActiveScheduleEvaluator.summarize(normalized) }
    val summary = if (counts.smsRanges == 0 && counts.appNotifyRanges == 0 && counts.callNotifyRanges == 0) {
        resolveSenderText(locale, ScheduleText.noRestrictions)
    } else {
        "${resolveSenderText(locale, ScheduleText.sms)} ${counts.smsRanges} · " +
            "${resolveSenderText(locale, ScheduleText.appNotify)} ${counts.appNotifyRanges} · " +
            "${resolveSenderText(locale, ScheduleText.callNotify)} ${counts.callNotifyRanges}"
    }

    fun updateRule(bucket: ScheduleBucket, nextRule: SenderActiveScheduleRule) {
        val next = when (bucket) {
            ScheduleBucket.SMS -> normalized.copy(sms = nextRule)
            ScheduleBucket.APP_NOTIFY -> normalized.copy(appNotify = nextRule)
            ScheduleBucket.CALL_NOTIFY -> normalized.copy(callNotify = nextRule)
        }
        onChange(SenderActiveScheduleEvaluator.sanitize(next))
    }

    SurfaceCard(
        title = resolveSenderText(locale, ScheduleText.title),
        subtitle = summary,
    ) {
        MetricRow(
            cards = listOf(
                {
                    ScheduleRuleCard(
                        title = resolveSenderText(locale, ScheduleText.sms),
                        rule = normalized.sms,
                        locale = locale,
                        onChange = { nextRule -> updateRule(ScheduleBucket.SMS, nextRule) },
                    )
                },
                {
                    ScheduleRuleCard(
                        title = resolveSenderText(locale, ScheduleText.appNotify),
                        rule = normalized.appNotify,
                        locale = locale,
                        onChange = { nextRule -> updateRule(ScheduleBucket.APP_NOTIFY, nextRule) },
                    )
                },
                {
                    ScheduleRuleCard(
                        title = resolveSenderText(locale, ScheduleText.callNotify),
                        rule = normalized.callNotify,
                        locale = locale,
                        onChange = { nextRule -> updateRule(ScheduleBucket.CALL_NOTIFY, nextRule) },
                    )
                },
            ),
        )
    }
}

@Composable
private fun ScheduleRuleCard(
    title: String,
    rule: SenderActiveScheduleRule,
    locale: DesktopLocale,
    onChange: (SenderActiveScheduleRule) -> Unit,
) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ConsoleInk,
                )
                RelaySwitch(
                    checked = rule.enabled,
                    onCheckedChange = { enabled ->
                        onChange(
                            if (enabled && rule.ranges.isEmpty()) {
                                SenderActiveScheduleRule(
                                    enabled = true,
                                    mode = SenderActiveScheduleConst.MODE_BLACKLIST,
                                    weekdays = SenderActiveScheduleConst.ALL_WEEKDAYS,
                                    ranges = listOf(SenderActiveScheduleRange(start = "09:00", end = "18:00")),
                                )
                            } else {
                                rule.copy(enabled = enabled)
                            },
                        )
                    },
                )
            }

            RelaySelect(
                value = rule.mode,
                options = listOf(
                    RelayOption(
                        value = SenderActiveScheduleConst.MODE_BLACKLIST,
                        label = resolveSenderText(locale, ScheduleText.blacklist),
                    ),
                    RelayOption(
                        value = SenderActiveScheduleConst.MODE_WHITELIST,
                        label = resolveSenderText(locale, ScheduleText.whitelist),
                    ),
                ),
                onValueChange = { mode -> onChange(rule.copy(mode = mode)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = resolveSenderText(locale, ScheduleText.weekdays),
                style = MaterialTheme.typography.labelLarge,
                color = ConsoleInk,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SCHEDULE_DAYS.forEach { (value, label) ->
                    val selected = value in rule.weekdays
                    WeekdayPill(
                        text = resolveSenderText(locale, label),
                        selected = selected,
                        onClick = {
                            val next = if (selected) {
                                rule.weekdays.filter { it != value }
                            } else {
                                (rule.weekdays + value).sorted()
                            }
                            onChange(rule.copy(weekdays = if (next.isEmpty()) rule.weekdays else next))
                        },
                    )
                }
            }

            Text(
                text = resolveSenderText(locale, ScheduleText.ranges),
                style = MaterialTheme.typography.labelLarge,
                color = ConsoleInk,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rule.ranges.forEachIndexed { index, range ->
                    TimeRangeRow(
                        range = range,
                        onChangeStart = { start ->
                            onChange(
                                rule.copy(
                                    ranges = rule.ranges.mapIndexed { current, item ->
                                        if (current == index) item.copy(start = start) else item
                                    },
                                ),
                            )
                        },
                        onChangeEnd = { end ->
                            onChange(
                                rule.copy(
                                    ranges = rule.ranges.mapIndexed { current, item ->
                                        if (current == index) item.copy(end = end) else item
                                    },
                                ),
                            )
                        },
                        onRemove = {
                            onChange(rule.copy(ranges = rule.ranges.filterIndexed { current, _ -> current != index }))
                        },
                        removeLabel = resolveSenderText(locale, ScheduleText.remove),
                    )
                }
            }
            ActionButton(
                text = resolveSenderText(locale, ScheduleText.addRange),
                onClick = {
                    onChange(
                        rule.copy(
                            ranges = rule.ranges + SenderActiveScheduleRange(start = "09:00", end = "18:00"),
                        ),
                    )
                },
                tone = ActionTone.NEUTRAL,
            )
        }
    }
}

@Composable
private fun WeekdayPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) Color(0xFFEDF6CF) else Color.White,
        border = BorderStroke(1.dp, if (selected) Color(0xFF7AA21D) else Color(0xFFD6DFB2)),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color(0xFF31411C) else Color(0xFF667451),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun TimeRangeRow(
    range: SenderActiveScheduleRange,
    onChangeStart: (String) -> Unit,
    onChangeEnd: (String) -> Unit,
    onRemove: () -> Unit,
    removeLabel: String,
) {
    var startText by remember(range.start) { mutableStateOf(range.start) }
    var endText by remember(range.end) { mutableStateOf(range.end) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = startText,
            onValueChange = { next ->
                startText = next
                if (SenderActiveScheduleConst.parseMinutes(next) != null) onChangeStart(next)
            },
            placeholder = { Text("HH:MM") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = endText,
            onValueChange = { next ->
                endText = next
                if (SenderActiveScheduleConst.parseMinutes(next) != null) onChangeEnd(next)
            },
            placeholder = { Text("HH:MM") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        ActionButton(text = removeLabel, onClick = onRemove, tone = ActionTone.DANGER)
    }
}
