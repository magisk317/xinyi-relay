package com.github.magisk317.smscode.ui.sender

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.magisk317.smscode.forwarder.entity.ForwardFilterRule
import com.github.magisk317.smscode.forwarder.filter.ForwardFilterConst
import com.github.magisk317.smscode.ui.forwardfilter.ForwardFilterMsgTypeTabs
import com.github.magisk317.smscode.ui.forwardfilter.ForwardFilterRuleEditorDialog
import com.github.magisk317.smscode.ui.forwardfilter.ForwardFilterRuleList

private data class SenderEditingRule(
    val id: Long,
    val policy: String,
    val matchMode: String,
    val pattern: String,
    val enabled: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderForwardFilterScreen(
    senderId: Long,
    onBack: () -> Unit,
    viewModel: SenderViewModel = viewModel(),
) {
    var msgType by remember { mutableStateOf(ForwardFilterConst.MSG_TYPE_SMS) }
    val rulesFlow = remember(senderId, msgType) { viewModel.senderForwardRulesFlow(senderId, msgType) }
    val rules by rulesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val senders by viewModel.senderList.collectAsStateWithLifecycle()
    val senderName = senders.firstOrNull { it.id == senderId }?.name.orEmpty()

    var editing by remember { mutableStateOf<SenderEditingRule?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (senderName.isNotBlank()) "通道关键词过滤 · $senderName" else "通道关键词过滤",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        editing = null
                        showEditor = true
                    }) {
                        Text("新增")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ForwardFilterMsgTypeTabs(
                selectedMsgType = msgType,
                onSelect = { msgType = it },
            )
            ForwardFilterRuleList(
                rules = rules,
                emptyText = "暂无规则",
                onToggleEnabled = { id, enabled -> viewModel.setForwardFilterRuleEnabled(id, enabled) },
                onEdit = { rule ->
                    editing = rule.toEditingRule()
                    showEditor = true
                },
                onDelete = { id -> viewModel.deleteForwardFilterRule(id) },
            )
        }
    }

    if (showEditor) {
        ForwardFilterRuleEditorDialog(
            title = if (editing == null) "新增规则" else "编辑规则",
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

private fun ForwardFilterRule.toEditingRule(): SenderEditingRule {
    return SenderEditingRule(
        id = id,
        policy = policy,
        matchMode = matchMode,
        pattern = pattern,
        enabled = enabled == 1,
    )
}
