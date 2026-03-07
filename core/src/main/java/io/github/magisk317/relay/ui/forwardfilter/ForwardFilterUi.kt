package io.github.magisk317.relay.ui.forwardfilter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.forwarder.entity.ForwardFilterRule
import io.github.magisk317.relay.forwarder.filter.ForwardFilterConst

private val msgTypeTabs = listOf(
    ForwardFilterConst.MSG_TYPE_SMS to "短信",
    ForwardFilterConst.MSG_TYPE_APP_NOTIFY to "应用通知",
)

@Composable
fun ForwardFilterMsgTypeTabs(
    selectedMsgType: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(selectedTabIndex = msgTypeTabs.indexOfFirst { it.first == selectedMsgType }.coerceAtLeast(0), modifier = modifier) {
        msgTypeTabs.forEach { (value, label) ->
            Tab(
                selected = selectedMsgType == value,
                onClick = { onSelect(value) },
                text = { Text(label) },
            )
        }
    }
}

@Composable
fun ForwardFilterRuleList(
    rules: List<ForwardFilterRule>,
    emptyText: String,
    channelIdLabelProvider: (ForwardFilterRule) -> String? = { null },
    onToggleEnabled: (Long, Boolean) -> Unit,
    onEdit: (ForwardFilterRule) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (rules.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        return
    }
    Column {
        rules.forEach { rule ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val policyLabel = if (rule.policy == ForwardFilterConst.POLICY_ALLOW) "白名单" else "黑名单"
                val modeLabel = if (rule.matchMode == ForwardFilterConst.MATCH_REGEX) "正则" else "包含"
                Text(
                    text = "[$policyLabel][$modeLabel] ${rule.pattern}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                channelIdLabelProvider(rule)?.takeIf { it.isNotBlank() }?.let { channelId ->
                    Text(
                        text = "渠道ID: $channelId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = rule.enabled == 1,
                            onCheckedChange = { onToggleEnabled(rule.id, it) },
                        )
                        Text(
                            text = if (rule.enabled == 1) "已启用" else "已停用",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Row {
                        IconButton(onClick = { onEdit(rule) }) {
                            Icon(imageVector = Icons.Outlined.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { onDelete(rule.id) }) {
                            Icon(imageVector = Icons.Outlined.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForwardFilterRuleEditorDialog(
    title: String,
    initialPolicy: String,
    initialMatchMode: String,
    initialPattern: String,
    initialEnabled: Boolean,
    showChannelInput: Boolean,
    initialChannelId: String,
    channelCandidates: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (
        policy: String,
        matchMode: String,
        pattern: String,
        enabled: Boolean,
        channelId: String,
    ) -> Unit,
) {
    var policy by remember(initialPolicy) { mutableStateOf(initialPolicy) }
    var matchMode by remember(initialMatchMode) { mutableStateOf(initialMatchMode) }
    var pattern by remember(initialPattern) { mutableStateOf(initialPattern) }
    var enabled by remember(initialEnabled) { mutableStateOf(initialEnabled) }
    var channelId by remember(initialChannelId) { mutableStateOf(initialChannelId) }

    val canSave = pattern.trim().isNotEmpty() && (!showChannelInput || channelId.trim().isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("策略", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { policy = ForwardFilterConst.POLICY_ALLOW },
                        label = { Text("白名单") },
                        enabled = policy != ForwardFilterConst.POLICY_ALLOW,
                    )
                    AssistChip(
                        onClick = { policy = ForwardFilterConst.POLICY_DENY },
                        label = { Text("黑名单") },
                        enabled = policy != ForwardFilterConst.POLICY_DENY,
                    )
                }

                Text("匹配方式", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { matchMode = ForwardFilterConst.MATCH_CONTAINS },
                        label = { Text("包含") },
                        enabled = matchMode != ForwardFilterConst.MATCH_CONTAINS,
                    )
                    AssistChip(
                        onClick = { matchMode = ForwardFilterConst.MATCH_REGEX },
                        label = { Text("正则") },
                        enabled = matchMode != ForwardFilterConst.MATCH_REGEX,
                    )
                }

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("关键词/表达式") },
                    supportingText = {
                        Text(if (matchMode == ForwardFilterConst.MATCH_REGEX) "使用 Kotlin Regex，忽略大小写" else "不区分大小写")
                    },
                    singleLine = false,
                    minLines = 2,
                )

                if (showChannelInput) {
                    OutlinedTextField(
                        value = channelId,
                        onValueChange = { channelId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("通知渠道ID") },
                        singleLine = true,
                    )
                    if (channelCandidates.isNotEmpty()) {
                        Text("历史候选", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            channelCandidates.take(6).forEach { candidate ->
                                AssistChip(
                                    onClick = { channelId = candidate },
                                    label = { Text(candidate, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("启用规则")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onConfirm(
                        policy.trim(),
                        matchMode.trim(),
                        pattern.trim(),
                        enabled,
                        channelId.trim(),
                    )
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
