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
import io.github.magisk317.relay.android.platform.sender.config.TelegramSetting
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.android.platform.sender.TelegramUtils
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import io.github.magisk317.relay.ui.sender.getSenderTypeName
import io.github.magisk317.relay.ui.sender.SenderViewModel
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.net.Proxy
import java.util.Date
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val showMessage: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    val activeScheduleEntry = LocalSenderActiveScheduleEntry.current
    val activeSchedule = activeScheduleEntry?.schedule ?: io.github.magisk317.relay.engine.sender.SenderActiveSchedule()
    var name by remember { mutableStateOf("") }
    var apiToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    var topicId by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("POST") }
    var parseMode by remember { mutableStateOf("HTML") }
    var proxyHost by remember { mutableStateOf("") }
    var proxyPort by remember { mutableStateOf("") }
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
                    Gson().fromJson(sender.jsonSetting, TelegramSetting::class.java)
                } catch (@Suppress("SwallowedException") e: com.google.gson.JsonSyntaxException) {
                    null
                }
                if (setting != null) {
                    apiToken = setting.apiToken
                    chatId = setting.chatId
                    topicId = setting.messageThreadId
                    method = setting.method
                    parseMode = setting.parseMode
                    proxyHost = setting.proxyHost
                    proxyPort = setting.proxyPort
                }
            }
        }
        isLoaded = true
    }

    fun buildSender(status: Int): Sender {
        val setting = TelegramSetting(
            apiToken = apiToken,
            chatId = chatId,
            messageThreadId = topicId,
            method = method,
            parseMode = parseMode,
            proxyHost = proxyHost,
            proxyPort = proxyPort,
            proxyType = Proxy.Type.DIRECT
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
            type = SenderType.TELEGRAM,
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
                            getSenderTypeName(context, SenderType.TELEGRAM),
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
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
                value = apiToken,
                onValueChange = { apiToken = it },
                label = { Text(stringResource(R.string.sender_form_label_bot_api_token_required)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = chatId,
                onValueChange = { chatId = it },
                label = { Text(stringResource(R.string.sender_form_label_chat_id_required)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = topicId,
                onValueChange = { topicId = it },
                label = { Text(stringResource(R.string.sender_form_label_topic_id_optional)) },
                supportingText = { Text(stringResource(R.string.sender_form_label_group_thread_id)) },
                modifier = Modifier.fillMaxWidth(),
            )
            SingleChoiceSegmentedSelector(
                options = listOf(
                    SegmentedOption("GET", stringResource(R.string.sender_segment_get)),
                    SegmentedOption("POST", stringResource(R.string.sender_segment_post)),
                ),
                selected = method,
                onSelect = { method = it },
            )
            SingleChoiceSegmentedSelector(
                options = listOf(
                    SegmentedOption("HTML", stringResource(R.string.sender_segment_html)),
                    SegmentedOption("MarkdownV2", stringResource(R.string.sender_segment_markdown_v2)),
                ),
                selected = parseMode,
                onSelect = { parseMode = it },
            )
            OutlinedTextField(
                value = proxyHost,
                onValueChange = { proxyHost = it },
                label = { Text(stringResource(R.string.sender_form_label_proxy_host)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = proxyPort,
                onValueChange = { proxyPort = it },
                label = { Text(stringResource(R.string.sender_form_label_proxy_port)) },
                modifier = Modifier.fillMaxWidth()
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
            Spacer(modifier = Modifier.height(8.dp))
            SenderTestActionRow(channel = "Telegram") {
                val setting = TelegramSetting(
                    apiToken = apiToken,
                    chatId = chatId,
                    messageThreadId = topicId,
                    method = method,
                    parseMode = parseMode,
                    proxyHost = proxyHost,
                    proxyPort = proxyPort,
                    proxyType = Proxy.Type.DIRECT,
                )
                val msg = buildSenderTestMsgInfo(context, getSenderTypeName(context, SenderType.TELEGRAM))
                TelegramUtils.sendMsg(setting, msg)
            }
        }
    }
}
