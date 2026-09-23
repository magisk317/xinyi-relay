package io.github.magisk317.relay.ui.home.forward

import io.github.magisk317.uikit.common.showLatestSnackbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.engine.filter.ForwardFilterConst
import io.github.magisk317.relay.ui.forwardfilter.ForwardFilterMsgTypeTabs
import io.github.magisk317.relay.ui.forwardfilter.ForwardFilterRuleEditorDialog
import io.github.magisk317.relay.ui.forwardfilter.ForwardFilterRuleList
import io.github.magisk317.relay.ui.forwardfilter.ForwardFilterEditorState
import io.github.magisk317.relay.ui.forwardfilter.ForwardFilterScreenScaffold
import io.github.magisk317.relay.ui.forwardfilter.toEditorState
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalForwardFilterScreen(
    onBack: () -> Unit,
    viewModel: ForwardFilterViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedSnackbarText = context.getString(R.string.pref_sync_snackbar)
    var msgType by remember { mutableStateOf(ForwardFilterConst.MSG_TYPE_SMS) }
    val rulesFlow = remember(msgType) { viewModel.globalForwardRulesFlow(msgType) }
    val rules by rulesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<ForwardFilterEditorState?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    ForwardFilterScreenScaffold(
        title = stringResource(id = R.string.forward_filter_global_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        selectedMsgType = msgType,
        onSelectMsgType = { msgType = it },
        rules = rules,
        onAdd = {
            editing = null
            showEditor = true
        },
        onToggleEnabled = { id, enabled ->
            viewModel.setForwardFilterRuleEnabled(id, enabled)
            scope.launch { snackbarHostState.showLatestSnackbar(savedSnackbarText) }
        },
        onEdit = { rule ->
            editing = rule.toEditorState()
            showEditor = true
        },
        onDelete = viewModel::deleteForwardFilterRule,
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

@Composable
fun AppForwardFilterScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: ForwardFilterViewModel = koinViewModel(),
) {
    val normalizedPackageName = remember(packageName) { packageName.trim() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedSnackbarText = context.getString(R.string.pref_sync_snackbar)
    val headerState by viewModel.appForwardFilterUiState.collectAsStateWithLifecycle()
    val appLabel = if (headerState.packageName == normalizedPackageName) {
        headerState.appLabel.ifBlank { normalizedPackageName }
    } else {
        normalizedPackageName
    }
    LaunchedEffect(normalizedPackageName) {
        viewModel.loadAppForwardFilterHeader(normalizedPackageName)
    }

    val packageRulesFlow = remember(normalizedPackageName) { viewModel.appPackageForwardRulesFlow(normalizedPackageName) }
    val packageRules by packageRulesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val channelRulesFlow = remember(normalizedPackageName) { viewModel.appChannelForwardRulesFlow(normalizedPackageName) }
    val channelRules by channelRulesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val channelCandidatesFlow = remember(normalizedPackageName) { viewModel.appNotifyChannelHistoryFlow(normalizedPackageName) }
    val channelCandidates by channelCandidatesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var editingPackageRule by remember { mutableStateOf<ForwardFilterEditorState?>(null) }
    var editingChannelRule by remember { mutableStateOf<ForwardFilterEditorState?>(null) }
    var showPackageEditor by remember { mutableStateOf(false) }
    var showChannelEditor by remember { mutableStateOf(false) }

    val appForwardFilterListState = rememberLazyListState()

    val appForwardFilterBody: @Composable (PaddingValues) -> Unit = { innerPadding ->
    LazyColumn(
        state = appForwardFilterListState,
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
                        title = stringResource(id = R.string.forward_filter_app_rules_title),
                        onAddClick = {
                            editingPackageRule = null
                            showPackageEditor = true
                        },
                    )
                    HorizontalDivider()
                    ForwardFilterRuleList(
                        rules = packageRules,
                        emptyText = stringResource(id = R.string.forward_filter_empty),
                        onToggleEnabled = { id, enabled ->
                            viewModel.setForwardFilterRuleEnabled(id, enabled)
                            scope.launch {
                                snackbarHostState.showLatestSnackbar(savedSnackbarText)
                            }
                        },
                        onEdit = { rule ->
                            editingPackageRule = rule.toEditorState()
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
                        title = stringResource(id = R.string.forward_filter_channel_rules_title),
                        onAddClick = {
                            editingChannelRule = null
                            showChannelEditor = true
                        },
                    )
                    HorizontalDivider()
                    ForwardFilterRuleList(
                        rules = channelRules,
                        emptyText = stringResource(id = R.string.forward_filter_empty),
                        channelIdLabelProvider = { rule ->
                            ForwardFilterConst.extractNotifyChannelId(rule.scopeKey, normalizedPackageName)
                        },
                        onToggleEnabled = { id, enabled ->
                            viewModel.setForwardFilterRuleEnabled(id, enabled)
                            scope.launch {
                                snackbarHostState.showLatestSnackbar(savedSnackbarText)
                            }
                        },
                        onEdit = { rule ->
                            editingChannelRule = rule.toEditorState(
                                channelId = ForwardFilterConst.extractNotifyChannelId(rule.scopeKey, normalizedPackageName),
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
                    text = stringResource(id = R.string.forward_filter_channel_history_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    }

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> AppForwardFilterScreenMiuix(
            title = appLabel,
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            listState = appForwardFilterListState,
            body = appForwardFilterBody,
        )

        UiKitStyle.Expressive -> AppForwardFilterScreenMaterial(
            title = appLabel,
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            listState = appForwardFilterListState,
            body = appForwardFilterBody,
        )
    }

    if (showPackageEditor) {
        ForwardFilterRuleEditorDialog(
            title = if (editingPackageRule == null) {
                stringResource(id = R.string.forward_filter_add_app_rule)
            } else {
                stringResource(id = R.string.forward_filter_edit_app_rule)
            },
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
                    scopeKey = normalizedPackageName,
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
            title = if (editingChannelRule == null) {
                stringResource(id = R.string.forward_filter_add_channel_rule)
            } else {
                stringResource(id = R.string.forward_filter_edit_channel_rule)
            },
            initialPolicy = editingChannelRule?.policy ?: ForwardFilterConst.POLICY_ALLOW,
            initialMatchMode = editingChannelRule?.matchMode ?: ForwardFilterConst.MATCH_CONTAINS,
            initialPattern = editingChannelRule?.pattern.orEmpty(),
            initialEnabled = editingChannelRule?.enabled ?: true,
            showChannelInput = true,
            initialChannelId = editingChannelRule?.channelId.orEmpty(),
            channelCandidates = channelCandidates,
            onDismiss = { showChannelEditor = false },
            onConfirm = { policy, matchMode, pattern, enabled, channelId ->
                val scopeKey = ForwardFilterConst.buildAndroidChannelScopeKey(normalizedPackageName, channelId)
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
            Text(stringResource(id = R.string.forward_filter_action_add))
        }
    }
}
