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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.domain.model.ForwardFilterRule
import io.github.magisk317.relay.domain.filter.ForwardFilterConst
import io.github.magisk317.relay.ui.common.CenteredChipText
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector

@Composable
fun ForwardFilterMsgTypeTabs(
    selectedMsgType: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val msgTypeTabs = listOf(
        ForwardFilterConst.MSG_TYPE_SMS to stringResource(id = R.string.forward_filter_msg_type_sms),
        ForwardFilterConst.MSG_TYPE_APP_NOTIFY to stringResource(id = R.string.forward_filter_msg_type_app_notify),
    )
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
                val policyLabel =
                    if (rule.policy == ForwardFilterConst.POLICY_ALLOW) {
                        stringResource(id = R.string.forward_filter_rule_allow)
                    } else {
                        stringResource(id = R.string.forward_filter_rule_deny)
                    }
                val modeLabel =
                    if (rule.matchMode == ForwardFilterConst.MATCH_REGEX) {
                        stringResource(id = R.string.forward_filter_rule_regex)
                    } else {
                        stringResource(id = R.string.forward_filter_rule_contains)
                    }
                Text(
                    text = "[$policyLabel][$modeLabel] ${rule.pattern}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                channelIdLabelProvider(rule)?.takeIf { it.isNotBlank() }?.let { channelId ->
                    Text(
                        text = stringResource(id = R.string.forward_filter_rule_channel_id, channelId),
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
                            text = if (rule.enabled == 1) {
                                stringResource(id = R.string.forward_filter_rule_enabled)
                            } else {
                                stringResource(id = R.string.forward_filter_rule_disabled)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Row {
                        IconButton(onClick = { onEdit(rule) }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(id = R.string.forward_filter_action_edit),
                            )
                        }
                        IconButton(onClick = { onDelete(rule.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(id = R.string.action_delete),
                            )
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
                Text(stringResource(id = R.string.forward_filter_strategy), style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedSelector(
                    options = listOf(
                        SegmentedOption(
                            ForwardFilterConst.POLICY_ALLOW,
                            stringResource(id = R.string.forward_filter_policy_allow),
                        ),
                        SegmentedOption(
                            ForwardFilterConst.POLICY_DENY,
                            stringResource(id = R.string.forward_filter_policy_deny),
                        ),
                    ),
                    selected = policy,
                    onSelect = { policy = it },
                )

                Text(stringResource(id = R.string.forward_filter_match_mode), style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedSelector(
                    options = listOf(
                        SegmentedOption(
                            ForwardFilterConst.MATCH_CONTAINS,
                            stringResource(id = R.string.forward_filter_match_contains),
                        ),
                        SegmentedOption(
                            ForwardFilterConst.MATCH_REGEX,
                            stringResource(id = R.string.forward_filter_match_regex),
                        ),
                    ),
                    selected = matchMode,
                    onSelect = { matchMode = it },
                )

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(id = R.string.forward_filter_pattern_label)) },
                    supportingText = {
                        Text(
                            if (matchMode == ForwardFilterConst.MATCH_REGEX) {
                                stringResource(id = R.string.forward_filter_pattern_regex_hint)
                            } else {
                                stringResource(id = R.string.forward_filter_pattern_contains_hint)
                            },
                        )
                    },
                    singleLine = false,
                    minLines = 2,
                )

                if (showChannelInput) {
                    OutlinedTextField(
                        value = channelId,
                        onValueChange = { channelId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(id = R.string.forward_filter_channel_id_label)) },
                        singleLine = true,
                    )
                    if (channelCandidates.isNotEmpty()) {
                        Text(stringResource(id = R.string.forward_filter_channel_history), style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            channelCandidates.take(6).forEach { candidate ->
                                AssistChip(
                                    onClick = { channelId = candidate },
                                    label = { CenteredChipText(candidate) },
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
                    Text(stringResource(id = R.string.forward_filter_enabled))
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
                Text(stringResource(id = R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
}
