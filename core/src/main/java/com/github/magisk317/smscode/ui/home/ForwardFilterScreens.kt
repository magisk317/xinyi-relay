package com.github.magisk317.smscode.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.magisk317.smscode.forwarder.entity.ForwardFilterRule
import com.github.magisk317.smscode.forwarder.filter.ForwardFilterConst
import com.github.magisk317.smscode.ui.forwardfilter.ForwardFilterMsgTypeTabs
import com.github.magisk317.smscode.ui.forwardfilter.ForwardFilterRuleEditorDialog
import com.github.magisk317.smscode.ui.forwardfilter.ForwardFilterRuleList
import org.koin.compose.viewmodel.koinViewModel

private data class EditingRule(
    val id: Long,
    val policy: String,
    val matchMode: String,
    val pattern: String,
    val enabled: Boolean,
    val channelId: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalForwardFilterScreen(
    onBack: () -> Unit,
    viewModel: AppConfigViewModel = koinViewModel(),
) {
    var msgType by remember { mutableStateOf(ForwardFilterConst.MSG_TYPE_SMS) }
    val rulesFlow = remember(msgType) { viewModel.globalForwardRulesFlow(msgType) }
    val rules by rulesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<EditingRule?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关键词过滤（全局）") },
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
                val rule = ForwardFilterRule(
                    id = editing?.id ?: 0L,
                    msgType = msgType,
                    scopeType = ForwardFilterConst.SCOPE_GLOBAL,
                    scopeKey = "",
                    senderId = 0L,
                    policy = policy,
                    matchMode = matchMode,
                    pattern = pattern,
                    enabled = if (enabled) 1 else 0,
                    updateTime = System.currentTimeMillis(),
                )
                viewModel.saveForwardFilterRule(rule)
                showEditor = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppForwardFilterScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: AppConfigViewModel = koinViewModel(),
) {
    val apps by viewModel.appsFlow.collectAsStateWithLifecycle()
    val app = apps.firstOrNull { it.packageName == packageName } ?: viewModel.getAppByPackageName(packageName)

    val packageRulesFlow = remember(packageName) { viewModel.appPackageForwardRulesFlow(packageName) }
    val packageRules by packageRulesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val channelRulesFlow = remember(packageName) { viewModel.appChannelForwardRulesFlow(packageName) }
    val channelRules by channelRulesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val channelCandidatesFlow = remember(packageName) { viewModel.appNotifyChannelHistoryFlow(packageName) }
    val channelCandidates by channelCandidatesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var editingPackageRule by remember { mutableStateOf<EditingRule?>(null) }
    var editingChannelRule by remember { mutableStateOf<EditingRule?>(null) }
    var showPackageEditor by remember { mutableStateOf(false) }
    var showChannelEditor by remember { mutableStateOf(false) }

    LaunchedEffect(packageName) {
        viewModel.refreshData(force = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = app?.label ?: packageName,
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
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "package_rules") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionHeaderRow(
                            title = "应用关键词规则",
                            onAddClick = {
                                editingPackageRule = null
                                showPackageEditor = true
                            },
                        )
                        HorizontalDivider()
                        ForwardFilterRuleList(
                            rules = packageRules,
                            emptyText = "暂无规则",
                            onToggleEnabled = { id, enabled -> viewModel.setForwardFilterRuleEnabled(id, enabled) },
                            onEdit = { rule ->
                                editingPackageRule = rule.toEditingRule()
                                showPackageEditor = true
                            },
                            onDelete = { id -> viewModel.deleteForwardFilterRule(id) },
                        )
                    }
                }
            }

            item(key = "channel_rules") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SectionHeaderRow(
                            title = "通知渠道ID规则",
                            onAddClick = {
                                editingChannelRule = null
                                showChannelEditor = true
                            },
                        )
                        HorizontalDivider()
                        ForwardFilterRuleList(
                            rules = channelRules,
                            emptyText = "暂无规则",
                            channelIdLabelProvider = { rule ->
                                ForwardFilterConst.extractNotifyChannelId(rule.scopeKey, packageName)
                            },
                            onToggleEnabled = { id, enabled -> viewModel.setForwardFilterRuleEnabled(id, enabled) },
                            onEdit = { rule ->
                                editingChannelRule = rule.toEditingRule(
                                    channelId = ForwardFilterConst.extractNotifyChannelId(rule.scopeKey, packageName),
                                )
                                showChannelEditor = true
                            },
                            onDelete = { id -> viewModel.deleteForwardFilterRule(id) },
                        )
                    }
                }
            }

            if (channelCandidates.isNotEmpty()) {
                item(key = "channel_hint") {
                    Text(
                        text = "历史候选来自近期应用通知日志，可直接点选填入渠道ID。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPackageEditor) {
        ForwardFilterRuleEditorDialog(
            title = if (editingPackageRule == null) "新增应用规则" else "编辑应用规则",
            initialPolicy = editingPackageRule?.policy ?: ForwardFilterConst.POLICY_ALLOW,
            initialMatchMode = editingPackageRule?.matchMode ?: ForwardFilterConst.MATCH_CONTAINS,
            initialPattern = editingPackageRule?.pattern.orEmpty(),
            initialEnabled = editingPackageRule?.enabled ?: true,
            showChannelInput = false,
            initialChannelId = "",
            channelCandidates = emptyList(),
            onDismiss = { showPackageEditor = false },
            onConfirm = { policy, matchMode, pattern, enabled, _ ->
                val rule = ForwardFilterRule(
                    id = editingPackageRule?.id ?: 0L,
                    msgType = ForwardFilterConst.MSG_TYPE_APP_NOTIFY,
                    scopeType = ForwardFilterConst.SCOPE_PACKAGE,
                    scopeKey = packageName,
                    senderId = 0L,
                    policy = policy,
                    matchMode = matchMode,
                    pattern = pattern,
                    enabled = if (enabled) 1 else 0,
                    updateTime = System.currentTimeMillis(),
                )
                viewModel.saveForwardFilterRule(rule)
                showPackageEditor = false
            },
        )
    }

    if (showChannelEditor) {
        ForwardFilterRuleEditorDialog(
            title = if (editingChannelRule == null) "新增渠道规则" else "编辑渠道规则",
            initialPolicy = editingChannelRule?.policy ?: ForwardFilterConst.POLICY_ALLOW,
            initialMatchMode = editingChannelRule?.matchMode ?: ForwardFilterConst.MATCH_CONTAINS,
            initialPattern = editingChannelRule?.pattern.orEmpty(),
            initialEnabled = editingChannelRule?.enabled ?: true,
            showChannelInput = true,
            initialChannelId = editingChannelRule?.channelId.orEmpty(),
            channelCandidates = channelCandidates,
            onDismiss = { showChannelEditor = false },
            onConfirm = { policy, matchMode, pattern, enabled, channelId ->
                val scopeKey = ForwardFilterConst.buildAndroidChannelScopeKey(packageName, channelId)
                if (scopeKey.isNotEmpty()) {
                    val rule = ForwardFilterRule(
                        id = editingChannelRule?.id ?: 0L,
                        msgType = ForwardFilterConst.MSG_TYPE_APP_NOTIFY,
                        scopeType = ForwardFilterConst.SCOPE_ANDROID_CHANNEL,
                        scopeKey = scopeKey,
                        senderId = 0L,
                        policy = policy,
                        matchMode = matchMode,
                        pattern = pattern,
                        enabled = if (enabled) 1 else 0,
                        updateTime = System.currentTimeMillis(),
                    )
                    viewModel.saveForwardFilterRule(rule)
                }
                showChannelEditor = false
            },
        )
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    onAddClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onAddClick) {
            Text("新增")
        }
    }
}

private fun ForwardFilterRule.toEditingRule(channelId: String = ""): EditingRule {
    return EditingRule(
        id = id,
        policy = policy,
        matchMode = matchMode,
        pattern = pattern,
        enabled = enabled == 1,
        channelId = channelId,
    )
}
