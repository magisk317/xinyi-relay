package io.github.magisk317.relay.ui.sender.forms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.domain.sender.SenderActiveSchedule

data class SenderNotifyScopeEntry(
    val senderId: Long,
    val summary: String,
    val onClick: (Long) -> Unit,
)

data class SenderForwardFilterEntry(
    val senderId: Long,
    val summary: String,
    val onClick: (Long) -> Unit,
)

data class SenderActiveScheduleEntry(
    val schedule: SenderActiveSchedule,
    val onChange: (SenderActiveSchedule) -> Unit,
)

val LocalSenderNotifyScopeEntry = staticCompositionLocalOf<SenderNotifyScopeEntry?> { null }
val LocalSenderForwardFilterEntry = staticCompositionLocalOf<SenderForwardFilterEntry?> { null }
val LocalSenderActiveScheduleEntry = staticCompositionLocalOf<SenderActiveScheduleEntry?> { null }

@Composable
fun ForwardToggleSection(
    receiveCode: Boolean,
    onReceiveCodeChange: (Boolean) -> Unit,
    receiveNonCode: Boolean,
    onReceiveNonCodeChange: (Boolean) -> Unit,
    receiveAppNotify: Boolean,
    onReceiveAppNotifyChange: (Boolean) -> Unit,
    receiveCallNotify: Boolean,
    onReceiveCallNotifyChange: (Boolean) -> Unit,
    activeSchedule: SenderActiveSchedule,
    onActiveScheduleChange: (SenderActiveSchedule) -> Unit,
) {
    val notifyScopeEntry = LocalSenderNotifyScopeEntry.current
    val forwardFilterEntry = LocalSenderForwardFilterEntry.current
    val context = LocalContext.current
    var showActiveScheduleDialog by remember { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.pref_forwarding_title), style = MaterialTheme.typography.titleSmall)
            ForwardToggleItem(
                title = stringResource(R.string.pref_forward_sms_code_title),
                summary = stringResource(R.string.pref_forward_sms_code_summary),
                checked = receiveCode,
                onCheckedChange = onReceiveCodeChange,
            )
            ForwardToggleItem(
                title = stringResource(R.string.pref_forward_sms_plain_title),
                summary = stringResource(R.string.pref_forward_sms_plain_summary),
                checked = receiveNonCode,
                onCheckedChange = onReceiveNonCodeChange,
            )
            ForwardToggleItem(
                title = stringResource(R.string.pref_forward_app_notify_title),
                summary = stringResource(R.string.pref_forward_app_notify_summary),
                checked = receiveAppNotify,
                onCheckedChange = onReceiveAppNotifyChange,
            )
            ForwardToggleItem(
                title = stringResource(R.string.pref_forward_call_notify_title),
                summary = stringResource(R.string.pref_forward_call_notify_summary),
                checked = receiveCallNotify,
                onCheckedChange = onReceiveCallNotifyChange,
            )
            ForwardConfigActionItem(
                title = stringResource(R.string.sender_active_schedule_title),
                summary = buildSenderActiveScheduleSummary(activeSchedule, context),
                onClick = { showActiveScheduleDialog = true },
            )
            if (notifyScopeEntry != null && notifyScopeEntry.senderId > 0L) {
                ForwardConfigActionItem(
                    title = stringResource(R.string.sender_notify_scope_title),
                    summary = notifyScopeEntry.summary,
                    onClick = { notifyScopeEntry.onClick(notifyScopeEntry.senderId) },
                )
            }
            if (forwardFilterEntry != null && forwardFilterEntry.senderId > 0L) {
                ForwardConfigActionItem(
                    title = stringResource(R.string.forward_filter_sender_title),
                    summary = forwardFilterEntry.summary,
                    onClick = { forwardFilterEntry.onClick(forwardFilterEntry.senderId) },
                )
            }
        }
    }
    if (showActiveScheduleDialog) {
        SenderActiveScheduleDialog(
            schedule = activeSchedule,
            onDismiss = { showActiveScheduleDialog = false },
            onConfirm = { nextSchedule ->
                onActiveScheduleChange(nextSchedule)
                showActiveScheduleDialog = false
            },
        )
    }
}

@Composable
private fun ForwardToggleItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ForwardConfigActionItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
