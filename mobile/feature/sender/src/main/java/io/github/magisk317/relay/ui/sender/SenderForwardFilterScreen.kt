@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.sender

import io.github.magisk317.uikit.common.showLatestSnackbar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.filter.ForwardFilterConst
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.ui.forwardfilter.ForwardFilterMsgTypeTabs
import io.github.magisk317.relay.ui.forwardfilter.ForwardFilterRuleEditorDialog
import io.github.magisk317.relay.ui.forwardfilter.ForwardFilterRuleList
import io.github.magisk317.relay.ui.forwardfilter.ForwardFilterEditorState
import io.github.magisk317.relay.ui.forwardfilter.ForwardFilterScreenScaffold
import io.github.magisk317.relay.ui.forwardfilter.toEditorState
import io.github.magisk317.uikit.surface.chromeTopAppBarColors
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderForwardFilterScreen(
    senderId: Long,
    onBack: () -> Unit,
    viewModel: SenderViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedSnackbarText = context.getString(R.string.pref_sync_snackbar)
    var msgType by remember { mutableStateOf(ForwardFilterConst.MSG_TYPE_SMS) }
    val rulesFlow = remember(senderId, msgType) { viewModel.senderForwardRulesFlow(senderId, msgType) }
    val rules by rulesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val senders by viewModel.senderList.collectAsStateWithLifecycle()
    val senderName = senders.firstOrNull { it.id == senderId }?.name.orEmpty()

    var editing by remember { mutableStateOf<ForwardFilterEditorState?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    ForwardFilterScreenScaffold(
        title = if (senderName.isNotBlank()) {
            stringResource(id = R.string.forward_filter_sender_title_with_name, senderName)
        } else {
            stringResource(id = R.string.forward_filter_sender_title)
        },
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
