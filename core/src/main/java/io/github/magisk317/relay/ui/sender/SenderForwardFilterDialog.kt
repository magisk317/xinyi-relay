@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.sender

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.domain.filter.ForwardFilterConst
import io.github.magisk317.relay.domain.model.ForwardFilterRule
import io.github.magisk317.relay.ui.forwardfilter.ForwardFilterMsgTypeTabs
import io.github.magisk317.relay.ui.forwardfilter.ForwardFilterRuleEditorDialog
import io.github.magisk317.relay.ui.forwardfilter.ForwardFilterRuleList

private data class SenderDialogEditingRule(
    val id: Long,
    val policy: String,
    val matchMode: String,
    val pattern: String,
    val enabled: Boolean,
)

@Composable
fun SenderForwardFilterDialog(
    senderId: Long,
    senderName: String,
    onDismiss: () -> Unit,
    viewModel: SenderViewModel,
) {
    val context = LocalContext.current
    var msgType by remember { mutableStateOf(ForwardFilterConst.MSG_TYPE_SMS) }
    val rulesFlow = remember(senderId, msgType) { viewModel.senderForwardRulesFlow(senderId, msgType) }
    val rules by rulesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<SenderDialogEditingRule?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (senderName.isNotBlank()) {
                    context.getString(R.string.forward_filter_sender_title_with_name, senderName)
                } else {
                    stringResource(id = R.string.forward_filter_sender_title)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ForwardFilterMsgTypeTabs(
                    selectedMsgType = msgType,
                    onSelect = { msgType = it },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            editing = null
                            showEditor = true
                        },
                    ) {
                        Text(stringResource(id = R.string.forward_filter_action_add))
                    }
                }
                if (rules.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.forward_filter_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ForwardFilterRuleList(
                        rules = rules,
                        emptyText = stringResource(id = R.string.forward_filter_empty),
                        onToggleEnabled = { id, enabled ->
                            viewModel.setForwardFilterRuleEnabled(id, enabled)
                        },
                        onEdit = { rule ->
                            editing = rule.toDialogEditingRule()
                            showEditor = true
                        },
                        onDelete = { id -> viewModel.deleteForwardFilterRule(id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.confirm))
            }
        },
    )

    if (showEditor) {
        ForwardFilterRuleEditorDialog(
            title = if (editing == null) {
                stringResource(id = R.string.forward_filter_add_rule)
            } else {
                stringResource(id = R.string.forward_filter_edit_rule)
            },
            initialPolicy = editing?.policy ?: ForwardFilterConst.POLICY_ALLOW,
            initialMatchMode = editing?.matchMode ?: ForwardFilterConst.MATCH_CONTAINS,
            initialPattern = editing?.pattern.orEmpty(),
            initialEnabled = editing?.enabled ?: true,
            showChannelInput = false,
            initialChannelId = "",
            channelCandidates = emptyList(),
            onDismiss = { showEditor = false },
            onConfirm = { policy, matchMode, pattern, enabled, _ ->
                viewModel.saveSenderForwardFilterRule(
                    senderId = senderId,
                    msgType = msgType,
                    ruleId = editing?.id ?: 0L,
                    policy = policy,
                    matchMode = matchMode,
                    pattern = pattern,
                    enabled = enabled,
                )
                showEditor = false
            },
        )
    }
}

private fun ForwardFilterRule.toDialogEditingRule(): SenderDialogEditingRule {
    return SenderDialogEditingRule(
        id = id,
        policy = policy,
        matchMode = matchMode,
        pattern = pattern,
        enabled = enabled == 1,
    )
}
