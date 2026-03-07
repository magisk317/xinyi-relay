package com.github.magisk317.smscode.ui.sender.forms

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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

val LocalSenderNotifyScopeEntry = staticCompositionLocalOf<SenderNotifyScopeEntry?> { null }
val LocalSenderForwardFilterEntry = staticCompositionLocalOf<SenderForwardFilterEntry?> { null }

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
) {
    val notifyScopeEntry = LocalSenderNotifyScopeEntry.current
    val forwardFilterEntry = LocalSenderForwardFilterEntry.current
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("转发开关", style = MaterialTheme.typography.titleSmall)
            ForwardToggleItem(
                title = "转发验证码短信",
                summary = "开启后接收验证码短信转发",
                checked = receiveCode,
                onCheckedChange = onReceiveCodeChange,
            )
            ForwardToggleItem(
                title = "转发非验证码短信",
                summary = "开启后所有短信都会转发",
                checked = receiveNonCode,
                onCheckedChange = onReceiveNonCodeChange,
            )
            ForwardToggleItem(
                title = "转发应用通知",
                summary = "开启后接收应用通知转发",
                checked = receiveAppNotify,
                onCheckedChange = onReceiveAppNotifyChange,
            )
            ForwardToggleItem(
                title = "转发通话通知",
                summary = "开启后接收来电/去电/未接等通话通知",
                checked = receiveCallNotify,
                onCheckedChange = onReceiveCallNotifyChange,
            )
            if (notifyScopeEntry != null && notifyScopeEntry.senderId > 0L) {
                ForwardConfigActionItem(
                    title = "通知应用范围",
                    summary = notifyScopeEntry.summary,
                    onClick = { notifyScopeEntry.onClick(notifyScopeEntry.senderId) },
                )
            }
            if (forwardFilterEntry != null && forwardFilterEntry.senderId > 0L) {
                ForwardConfigActionItem(
                    title = "通道关键词过滤",
                    summary = forwardFilterEntry.summary,
                    onClick = { forwardFilterEntry.onClick(forwardFilterEntry.senderId) },
                )
            }
        }
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
