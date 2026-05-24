package io.github.magisk317.relay.ui.sender.forms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDrafts
import io.github.magisk317.relay.ui.sender.getSenderTypeName
import io.github.magisk317.relay.ui.sender.SenderViewModel
import kotlinx.coroutines.launch
import java.util.Date
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeworkAgentConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current


    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val activeScheduleEntry = LocalSenderActiveScheduleEntry.current
    val activeSchedule = activeScheduleEntry?.schedule ?: io.github.magisk317.relay.engine.sender.SenderActiveSchedule()
    var name by remember { mutableStateOf("") }
    var corpID by remember { mutableStateOf("") }
    var agentID by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var toUser by remember { mutableStateOf("@all") }
    var customizeAPI by remember { mutableStateOf("https://qyapi.weixin.qq.com") }
    var receiveCode by remember { mutableStateOf(true) }
    var receiveNonCode by remember { mutableStateOf(true) }
    var receiveAppNotify by remember { mutableStateOf(true) }
    var receiveCallNotify by remember { mutableStateOf(false) }
    var currentSender by remember { mutableStateOf<Sender?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(senderId) {
        if (senderId > 0) {
            viewModel.getSender(senderId)?.let { sender ->
                currentSender = sender
                name = sender.name
                receiveCode = sender.receiveCode == 1
                receiveNonCode = sender.receiveNonCode == 1
                receiveAppNotify = sender.receiveAppNotify == 1
                receiveCallNotify = sender.receiveCallNotify == 1
                val draft = SenderSettingDrafts.fromSender(sender)
                corpID = draft.string("corpID")
                agentID = draft.string("agentID")
                secret = draft.string("secret")
                toUser = draft.string("toUser").ifBlank { "@all" }
                customizeAPI = draft.string("customizeAPI").ifBlank { "https://qyapi.weixin.qq.com" }
            }
        }
    }

    fun buildSender(status: Int): Sender {
        val json = SenderSettingDrafts.empty(SenderType.WEWORK_AGENT)
            .withString("corpID", corpID)
            .withString("agentID", agentID)
            .withString("secret", secret)
            .withString("toUser", toUser)
            .withString("customizeAPI", customizeAPI)
            .withString("proxyType", "DIRECT")
            .toJson()
        return currentSender?.copy(
            name = name,
            jsonSetting = json,
            status = status,
            receiveCode = if (receiveCode) 1 else 0,
            receiveNonCode = if (receiveNonCode) 1 else 0,
            receiveAppNotify = if (receiveAppNotify) 1 else 0,
            receiveCallNotify = if (receiveCallNotify) 1 else 0,
            activeSchedule = activeSchedule,
            time = Date(),
        ) ?: Sender(
            id = 0,
            type = SenderType.WEWORK_AGENT,
            name = name,
            jsonSetting = json,
            status = status,
            receiveCode = if (receiveCode) 1 else 0,
            receiveNonCode = if (receiveNonCode) 1 else 0,
            receiveAppNotify = if (receiveAppNotify) 1 else 0,
            receiveCallNotify = if (receiveCallNotify) 1 else 0,
            activeSchedule = activeSchedule,
            time = Date(),
        )
    }

    BackHandler { showExitDialog = true }

    if (showExitDialog) {
        DraftExitDialog(
            onSaveDraft = {
                scope.launch {
                    runCatching { viewModel.saveSenderSync(buildSender(status = 0)) }
                        .onSuccess {
                            showMessage(context.getString(R.string.sender_form_draft_saved))
                            showExitDialog = false
                            onBack()
                        }
                        .onFailure { showMessage(context.getString(R.string.sender_form_draft_save_failed, it.message.orEmpty())) }
                }
            },
            onDiscard = {
                showExitDialog = false
                onBack()
            },
            onCancel = { showExitDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        context.getString(
                            if (senderId == 0L) R.string.sender_form_create_title else R.string.sender_form_edit_title,
                            getSenderTypeName(context, SenderType.WEWORK_AGENT),
                        ),
                    )
                },
                navigationIcon = { IconButton(onClick = { showExitDialog = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { viewModel.saveSenderSync(buildSender(status = 1)) }
                                .onSuccess {
                                    showMessage(context.getString(R.string.sender_form_save_success))
                                    onBack()
                                }
                                .onFailure { showMessage(context.getString(R.string.sender_form_save_failed, it.message.orEmpty())) }
                        }
                    }) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text(stringResource(R.string.sender_form_name_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                corpID,
                { corpID = it },
                label = { Text(stringResource(R.string.sender_form_label_corp_id)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                agentID,
                { agentID = it },
                label = { Text(stringResource(R.string.sender_form_label_agent_id)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                secret,
                { secret = it },
                label = { Text(stringResource(R.string.sender_form_label_secret)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                toUser,
                { toUser = it },
                label = { Text(stringResource(R.string.sender_form_label_to_user)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                customizeAPI,
                { customizeAPI = it },
                label = { Text(stringResource(R.string.sender_form_label_api_base)) },
                modifier = Modifier.fillMaxWidth(),
            )
            ForwardToggleSection(
                receiveCode = receiveCode,
                onReceiveCodeChange = { receiveCode = it },
                receiveNonCode = receiveNonCode,
                onReceiveNonCodeChange = { receiveNonCode = it },
                receiveAppNotify = receiveAppNotify,
                onReceiveAppNotifyChange = { receiveAppNotify = it },
                receiveCallNotify = receiveCallNotify,
                onReceiveCallNotifyChange = { receiveCallNotify = it },
                activeSchedule = activeSchedule,
                onActiveScheduleChange = { activeScheduleEntry?.onChange(it) },
            )
            SenderTestActionRow(
                channel = "WeworkAgent",
                viewModel = viewModel,
                senderType = SenderType.WEWORK_AGENT,
            ) {
                buildSender(status = 1)
            }
        }
    }
}
