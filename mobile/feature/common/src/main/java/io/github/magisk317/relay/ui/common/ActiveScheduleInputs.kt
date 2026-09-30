package io.github.magisk317.relay.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.sender.SenderActiveScheduleConst
import io.github.magisk317.uikit.surface.AppAlertDialog
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.AppTextButton

@Composable
fun ActiveScheduleWeekdayRow(
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
fun ActiveScheduleTimeValueButton(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    AppSecondaryButton(
        onClick = { showPicker = true },
        modifier = modifier.fillMaxWidth(),
    ) {
        AppText(
            text = value,
            role = AppTextRole.Subtitle,
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

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(stringResource(R.string.sender_active_schedule_pick_time)) },
        text = {
            TimePicker(state = pickerState)
        },
        confirmButton = {
            AppTextButton(text = stringResource(R.string.confirm), onClick = {
                    onConfirm(
                        String.format(
                            Locale.US,
                            "%02d:%02d",
                            pickerState.hour,
                            pickerState.minute,
                        ),
                    )
                })
        },
        dismissButton = {
            AppTextButton(text = stringResource(R.string.cancel), onClick = onDismiss)
        },
    )
}
