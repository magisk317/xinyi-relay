package com.github.magisk317.smscode.ui.sender.forms

import android.widget.Toast
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.Sender
import com.github.magisk317.smscode.forwarder.entity.setting.FeishuAppSetting
import com.github.magisk317.smscode.forwarder.utils.SenderType
import com.github.magisk317.smscode.forwarder.utils.sender.FeishuAppUtils
import com.github.magisk317.smscode.ui.sender.SenderViewModel
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeishuAppConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var appId by remember { mutableStateOf("") }
    var appSecret by remember { mutableStateOf("") }
    var receiveId by remember { mutableStateOf("") }
    var msgType by remember { mutableStateOf("interactive") }
    var titleTemplate by remember { mutableStateOf("") }
    var receiveIdType by remember { mutableStateOf("user_id") }
    var messageCard by remember { mutableStateOf("") }
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
                runCatching { Gson().fromJson(sender.jsonSetting, FeishuAppSetting::class.java) }.getOrNull()?.let {
                    appId = it.appId
                    appSecret = it.appSecret
                    receiveId = it.receiveId
                    msgType = it.msgType
                    titleTemplate = it.titleTemplate
                    receiveIdType = it.receiveIdType
                    messageCard = it.messageCard
                }
            }
        }
    }

    fun buildSender(status: Int): Sender {
        val setting = FeishuAppSetting(
            appId = appId,
            appSecret = appSecret,
            receiveId = receiveId,
            msgType = msgType,
            titleTemplate = titleTemplate,
            receiveIdType = receiveIdType,
            messageCard = messageCard,
        )
        return currentSender?.copy(
            name = name,
            jsonSetting = Gson().toJson(setting),
            status = status,
            receiveCode = if (receiveCode) 1 else 0,
            receiveNonCode = if (receiveNonCode) 1 else 0,
            receiveAppNotify = if (receiveAppNotify) 1 else 0,
            receiveCallNotify = if (receiveCallNotify) 1 else 0,
            time = Date(),
        ) ?: Sender(
            id = 0,
            type = SenderType.FEISHU_APP,
            name = name,
            jsonSetting = Gson().toJson(setting),
            status = status,
            receiveCode = if (receiveCode) 1 else 0,
            receiveNonCode = if (receiveNonCode) 1 else 0,
            receiveAppNotify = if (receiveAppNotify) 1 else 0,
            receiveCallNotify = if (receiveCallNotify) 1 else 0,
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
                            Toast.makeText(context, "信息已保存", Toast.LENGTH_SHORT).show()
                            showExitDialog = false
                            onBack()
                        }
                        .onFailure { Toast.makeText(context, "保存草稿失败: ${it.message}", Toast.LENGTH_LONG).show() }
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
                title = { Text(if (senderId == 0L) "新增 飞书应用" else "编辑 飞书应用") },
                navigationIcon = { IconButton(onClick = { showExitDialog = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { viewModel.saveSenderSync(buildSender(status = 1)) }
                                .onSuccess {
                                    Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                                .onFailure { Toast.makeText(context, "保存失败: ${it.message}", Toast.LENGTH_LONG).show() }
                        }
                    }) { Text("保存") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("通道名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(appId, { appId = it }, label = { Text("App ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(appSecret, { appSecret = it }, label = { Text("App Secret") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(receiveId, { receiveId = it }, label = { Text("Receive ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(receiveIdType, { receiveIdType = it }, label = { Text("receive_id_type") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = msgType == "interactive", onClick = { msgType = "interactive" }, label = { Text("Interactive") })
                FilterChip(selected = msgType == "text", onClick = { msgType = "text" }, label = { Text("Text") })
            }
            OutlinedTextField(titleTemplate, { titleTemplate = it }, label = { Text("标题模板") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(messageCard, { messageCard = it }, label = { Text("消息卡片JSON(可选)") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
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
            SenderTestActionRow(channel = "FeishuApp") {
                FeishuAppUtils.sendMsg(
                    FeishuAppSetting(
                        appId = appId,
                        appSecret = appSecret,
                        receiveId = receiveId,
                        msgType = msgType,
                        titleTemplate = titleTemplate,
                        receiveIdType = receiveIdType,
                        messageCard = messageCard,
                    ),
                    MsgInfo("sms", "10086", "飞书应用测试消息", Date(), "SIM1"),
                )
            }
        }
    }
}
