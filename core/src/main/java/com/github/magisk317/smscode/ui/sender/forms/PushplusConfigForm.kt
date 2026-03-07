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
import com.github.magisk317.smscode.forwarder.entity.setting.PushplusSetting
import com.github.magisk317.smscode.forwarder.utils.SenderType
import com.github.magisk317.smscode.forwarder.utils.sender.PushplusUtils
import com.github.magisk317.smscode.ui.sender.SenderViewModel
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushplusConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
                title = { Text(if (senderId == 0L) "新增 Pushplus" else "编辑 Pushplus") },
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
            OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Token (必填)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("群组编码 topic (选填)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = template, onValueChange = { template = it }, label = { Text("消息模板 template") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = channel, onValueChange = { channel = it }, label = { Text("发送渠道 channel") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = website, onValueChange = { website = it }, label = { Text("请求地址 website") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = titleTemplate, onValueChange = { titleTemplate = it }, label = { Text("自定义标题模板") }, modifier = Modifier.fillMaxWidth())

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
                val msg = MsgInfo(
                    type = "sms",
                    from = "10086",
                    content = "Pushplus 连通性测试消息",
                    date = Date(),
                    simInfo = "SIM1",
                )
                PushplusUtils.sendMsg(setting, msg)
            }
        }
    }
}
