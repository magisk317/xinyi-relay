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
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.model.MsgInfo
import io.github.magisk317.relay.model.Sender
import io.github.magisk317.relay.model.setting.GotifySetting
import io.github.magisk317.relay.domain.sender.SenderType
import io.github.magisk317.relay.forwarder.utils.sender.GotifyUtils
import io.github.magisk317.relay.ui.sender.SenderViewModel
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.Date
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GotifyConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current


    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    var name by remember { mutableStateOf("") }
    var webServer by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("0") }
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
                runCatching { Gson().fromJson(sender.jsonSetting, GotifySetting::class.java) }.getOrNull()?.let {
                    webServer = it.webServer
                    title = it.title
                    priority = it.priority
                }
            }
        }
    }

    fun buildSender(status: Int): Sender {
        val setting = GotifySetting(webServer = webServer, title = title, priority = priority)
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
            type = SenderType.GOTIFY,
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
            onCancel = { showExitDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (senderId == 0L) "新增 Gotify" else "编辑 Gotify") },
                navigationIcon = { IconButton(onClick = { showExitDialog = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
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
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("通道名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(webServer, { webServer = it }, label = { Text("Gotify 地址") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                title,
                { title = it },
                label = { Text("标题") },
                placeholder = { Text("默认为信息驿站，可自行修改") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(priority, { priority = it }, label = { Text("优先级") }, modifier = Modifier.fillMaxWidth())
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
            SenderTestActionRow(channel = "Gotify") {
                GotifyUtils.sendMsg(
                    GotifySetting(webServer = webServer, title = title, priority = priority),
                    MsgInfo("sms", "10086", "Gotify 测试消息", Date(), "SIM1"),
                )
            }
        }
    }
}