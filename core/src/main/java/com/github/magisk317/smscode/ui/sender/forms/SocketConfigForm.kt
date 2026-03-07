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
import com.github.magisk317.smscode.forwarder.entity.setting.SocketSetting
import com.github.magisk317.smscode.forwarder.utils.SenderType
import com.github.magisk317.smscode.forwarder.utils.sender.SocketUtils
import com.github.magisk317.smscode.ui.sender.SenderViewModel
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocketConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("MQTT") }
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("0") }
    var msgTemplate by remember { mutableStateOf("{\"msg\":\"[msg]\"}") }
    var outTopic by remember { mutableStateOf("smscode/default") }
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
                runCatching { Gson().fromJson(sender.jsonSetting, SocketSetting::class.java) }.getOrNull()?.let {
                    method = it.method
                    address = it.address
                    port = it.port.toString()
                    msgTemplate = it.msgTemplate
                    outTopic = it.outMessageTopic
                }
            }
        }
    }

    fun buildSender(status: Int): Sender {
        val setting = SocketSetting(
            method = method,
            address = address,
            port = port.toIntOrNull() ?: 0,
            msgTemplate = msgTemplate,
            outMessageTopic = outTopic,
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
            type = SenderType.SOCKET,
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
                title = { Text(if (senderId == 0L) "新增 Socket 通道" else "编辑 Socket 通道") },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = method == "TCP", onClick = { method = "TCP" }, label = { Text("TCP") })
                FilterChip(selected = method == "UDP", onClick = { method = "UDP" }, label = { Text("UDP") })
                FilterChip(selected = method == "MQTT", onClick = { method = "MQTT" }, label = { Text("MQTT") })
            }
            OutlinedTextField(address, { address = it }, label = { Text("地址") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(port, { port = it }, label = { Text("端口") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(msgTemplate, { msgTemplate = it }, label = { Text("消息模板") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            OutlinedTextField(outTopic, { outTopic = it }, label = { Text("MQTT输出Topic") }, modifier = Modifier.fillMaxWidth())
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
            SenderTestActionRow(channel = "Socket") {
                SocketUtils.sendMsg(
                    SocketSetting(
                        method = method,
                        address = address,
                        port = port.toIntOrNull() ?: 0,
                        msgTemplate = msgTemplate,
                        outMessageTopic = outTopic,
                    ),
                    MsgInfo("sms", "10086", "Socket 测试消息", Date(), "SIM1"),
                )
            }
        }
    }
}
