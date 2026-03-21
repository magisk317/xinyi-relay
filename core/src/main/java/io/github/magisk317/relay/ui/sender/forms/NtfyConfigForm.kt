package io.github.magisk317.relay.ui.sender.forms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.google.gson.Gson
import io.github.magisk317.relay.model.MsgInfo
import io.github.magisk317.relay.model.Sender
import io.github.magisk317.relay.model.setting.NtfySetting
import io.github.magisk317.relay.domain.sender.SenderType
import io.github.magisk317.relay.forwarder.utils.sender.NtfyUtils
import io.github.magisk317.relay.ui.sender.SenderViewModel
import java.util.Date
import kotlinx.coroutines.launch
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NtfyConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current


    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("3") }
    var tags by remember { mutableStateOf("") }
    var receiveCode by remember { mutableStateOf(true) }
    var receiveNonCode by remember { mutableStateOf(true) }
    var receiveAppNotify by remember { mutableStateOf(true) }
    var receiveCallNotify by remember { mutableStateOf(false) }
    var currentSender by remember { mutableStateOf<Sender?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(senderId) {
        if (senderId > 0L) {
            viewModel.getSender(senderId)?.let { sender ->
                currentSender = sender
                name = sender.name
                receiveCode = sender.receiveCode == 1
                receiveNonCode = sender.receiveNonCode == 1
                receiveAppNotify = sender.receiveAppNotify == 1
                receiveCallNotify = sender.receiveCallNotify == 1
                runCatching { Gson().fromJson(sender.jsonSetting, NtfySetting::class.java) }.getOrNull()?.let {
                    server = it.server
                    topic = it.topic
                    token = it.token
                    title = it.title
                    priority = it.priority
                    tags = it.tags
                }
            }
        }
    }

    fun buildSender(status: Int): Sender {
        val setting = NtfySetting(
            server = server,
            topic = topic,
            token = token,
            title = title,
            priority = priority,
            tags = tags,
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
            id = 0L,
            type = SenderType.NTFY,
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
                            showMessage("信息已保存")
                            showExitDialog = false
                            onBack()
                        }
                        .onFailure { showMessage("保存草稿失败: ${it.message}") }
                }
            },
            onDiscard = {
                showExitDialog = false
                onBack()
            },
            onCancel = { showExitDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (senderId == 0L) "新增 ntfy" else "编辑 ntfy") },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { viewModel.saveSenderSync(buildSender(status = 1)) }
                                .onSuccess {
                                    showMessage("保存成功")
                                    onBack()
                                }
                                .onFailure { showMessage("保存失败: ${it.message}") }
                        }
                    }) { Text("保存") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("通道名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text("Server (必填)") },
                supportingText = { Text("例如 https://ntfy.sh") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(topic, { topic = it }, label = { Text("Topic (必填)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it }, label = { Text("Bearer Token (选填)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                title,
                { title = it },
                label = { Text("标题 (选填)") },
                placeholder = { Text("默认为信息驿站，可自行修改") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                priority,
                { priority = it },
                label = { Text("优先级 (1-5)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                tags,
                { tags = it },
                label = { Text("Tags (选填)") },
                supportingText = { Text("逗号分隔，如 sms,android") },
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
            )
            Spacer(modifier = Modifier.height(8.dp))
            SenderTestActionRow(channel = "Ntfy") {
                NtfyUtils.sendMsg(
                    NtfySetting(
                        server = server,
                        topic = topic,
                        token = token,
                        title = title,
                        priority = priority,
                        tags = tags,
                    ),
                    MsgInfo(
                        type = "sms",
                        from = "10086",
                        content = "ntfy 连通性测试消息",
                        date = Date(),
                        simInfo = "SIM1",
                    ),
                )
            }
        }
    }
}