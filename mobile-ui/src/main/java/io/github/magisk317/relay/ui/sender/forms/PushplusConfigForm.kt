package io.github.magisk317.relay.ui.sender.forms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.sender.config.PushplusSetting
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.PushplusUtils
import io.github.magisk317.relay.ui.sender.getSenderTypeName
import io.github.magisk317.relay.ui.sender.SenderViewModel
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.Date
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushplusConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val showMessage: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    val activeScheduleEntry = LocalSenderActiveScheduleEntry.current
    val activeSchedule = activeScheduleEntry?.schedule ?: io.github.magisk317.relay.engine.sender.SenderActiveSchedule()
    var name by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("html") }
    var channel by remember { mutableStateOf("wechat") }
    var website by remember { mutableStateOf("www.pushplus.plus") }
    var titleTemplate by remember { mutableStateOf("") }
    var receiveCode by remember { mutableStateOf(true) }
    var receiveNonCode by remember { mutableStateOf(true) }
    var receiveAppNotify by remember { mutableStateOf(true) }
    var receiveCallNotify by remember { mutableStateOf(false) }

    var isLoaded by remember { mutableStateOf(false) }
    var currentSender by remember { mutableStateOf<Sender?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(senderId) {
        if (senderId > 0) {
            val sender = viewModel.getSender(senderId)
            if (sender != null) {
                currentSender = sender
                name = sender.name
                receiveCode = sender.receiveCode == 1
                receiveNonCode = sender.receiveNonCode == 1
                receiveAppNotify = sender.receiveAppNotify == 1
                receiveCallNotify = sender.receiveCallNotify == 1
                val setting = try {
                    Gson().fromJson(sender.jsonSetting, PushplusSetting::class.java)
                } catch (@Suppress("SwallowedException") e: com.google.gson.JsonSyntaxException) {
                    null
                }
                if (setting != null) {
                    token = setting.token
                    topic = setting.topic
                    template = setting.template
                    channel = setting.channel
                    website = setting.website
                    titleTemplate = setting.titleTemplate
                }
            }
        }
        isLoaded = true
    }

    fun buildSender(status: Int): Sender {
        val setting = PushplusSetting(
            website = website,
            token = token,
            topic = topic,
            template = template,
            channel = channel,
            titleTemplate = titleTemplate
        )
        val json = Gson().toJson(setting)
        return currentSender?.copy(
            name = name,
            jsonSetting = json,
            status = status,
            receiveCode = if (receiveCode) 1 else 0,
            receiveNonCode = if (receiveNonCode) 1 else 0,
            receiveAppNotify = if (receiveAppNotify) 1 else 0,
            receiveCallNotify = if (receiveCallNotify) 1 else 0,
            activeSchedule = activeSchedule,
            time = Date()
        ) ?: Sender(
            id = 0,
            type = SenderType.PUSHPLUS,
            name = name,
            jsonSetting = json,
            status = status,
            receiveCode = if (receiveCode) 1 else 0,
            receiveNonCode = if (receiveNonCode) 1 else 0,
            receiveAppNotify = if (receiveAppNotify) 1 else 0,
            receiveCallNotify = if (receiveCallNotify) 1 else 0,
            activeSchedule = activeSchedule,
            time = Date()
        )
    }

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        DraftExitDialog(
            onSaveDraft = {
                coroutineScope.launch {
                    runCatching { viewModel.saveSenderSync(buildSender(status = 0)) }
                        .onSuccess {
                            showMessage(context.getString(R.string.sender_form_draft_saved))
                            showExitDialog = false
                            onBack()
                        }
                        .onFailure { e ->
                            showMessage(context.getString(R.string.sender_form_draft_save_failed, e.message.orEmpty()))
                        }
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
                            getSenderTypeName(context, SenderType.PUSHPLUS),
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            runCatching { viewModel.saveSenderSync(buildSender(status = 1)) }
                                .onSuccess {
                                    showMessage(context.getString(R.string.sender_form_save_success))
                                    onBack()
                                }
                                .onFailure { e ->
                                    showMessage(context.getString(R.string.sender_form_save_failed, e.message.orEmpty()))
                                }
                        }
                    }) { Text(stringResource(R.string.save)) }
                }
            )
        }
    ) { padding ->
        if (!isLoaded) return@Scaffold

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.sender_form_name_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(stringResource(R.string.sender_form_label_token_required)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text(stringResource(R.string.sender_form_label_topic_code_optional)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = template,
                onValueChange = { template = it },
                label = { Text(stringResource(R.string.sender_form_label_message_template)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = channel,
                onValueChange = { channel = it },
                label = { Text(stringResource(R.string.sender_form_label_delivery_channel)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = website,
                onValueChange = { website = it },
                label = { Text(stringResource(R.string.sender_form_label_request_url)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = titleTemplate,
                onValueChange = { titleTemplate = it },
                label = { Text(stringResource(R.string.sender_form_title_template_label)) },
                placeholder = { Text(stringResource(R.string.sender_form_title_template_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))
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
            Spacer(modifier = Modifier.height(8.dp))
            SenderTestActionRow(channel = "Pushplus") {
                val setting = PushplusSetting(
                    website = website,
                    token = token,
                    topic = topic,
                    template = template,
                    channel = channel,
                    titleTemplate = titleTemplate,
                )
                val msg = buildSenderTestMsgInfo(context, getSenderTypeName(context, SenderType.PUSHPLUS))
                PushplusUtils.sendMsg(setting, msg)
            }
        }
    }
}
