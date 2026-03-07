package com.github.magisk317.smscode.ui.sender.forms

import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.Sender
import com.github.magisk317.smscode.forwarder.entity.setting.TelegramSetting
import com.github.magisk317.smscode.forwarder.utils.SenderType
import com.github.magisk317.smscode.forwarder.utils.sender.TelegramUtils
import com.github.magisk317.smscode.ui.sender.SenderViewModel
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.net.Proxy
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
                            Toast.makeText(context, "信息已保存", Toast.LENGTH_SHORT).show()
                            showExitDialog = false
                            onBack()
                        }
                        .onFailure { e ->
                            Toast.makeText(context, "保存草稿失败: ${e.message}", Toast.LENGTH_LONG).show()
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
                title = { Text(if (senderId == 0L) "新增 Telegram" else "编辑 Telegram") },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            runCatching { viewModel.saveSenderSync(buildSender(status = 1)) }
                                .onSuccess {
                                    Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                                .onFailure { e ->
                                    Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                    }) { Text("保存") }
                }
            )
        }
    ) { padding ->
        if (!isLoaded) return@Scaffold

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("通道名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = apiToken, onValueChange = { apiToken = it }, label = { Text("Bot API Token (必填)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = chatId, onValueChange = { chatId = it }, label = { Text("Chat ID (必填)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = topicId,
                onValueChange = { topicId = it },
                label = { Text("Topic ID (选填)") },
                supportingText = { Text("群组话题 Thread ID") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = method == "GET", onClick = { method = "GET" }, label = { Text("GET") })
                FilterChip(selected = method == "POST", onClick = { method = "POST" }, label = { Text("POST") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = parseMode == "HTML", onClick = { parseMode = "HTML" }, label = { Text("HTML") })
                FilterChip(selected = parseMode == "MarkdownV2", onClick = { parseMode = "MarkdownV2" }, label = { Text("MarkdownV2") })
            }
            OutlinedTextField(
                value = proxyHost,
                onValueChange = { proxyHost = it },
                label = { Text("Proxy Host (如 127.0.0.1)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = proxyPort,
                onValueChange = { proxyPort = it },
                label = { Text("Proxy Port (如 7890)") },
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
                val msg = MsgInfo(
                    type = "sms",
                    from = "10086",
                    content = "Telegram 连通性测试消息",
                    date = Date(),
                    simInfo = "SIM1",
                )
                TelegramUtils.sendMsg(setting, msg)
            }
        }
    }
}
