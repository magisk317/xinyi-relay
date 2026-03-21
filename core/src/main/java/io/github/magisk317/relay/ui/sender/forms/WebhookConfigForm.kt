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
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.BuildConfig
import io.github.magisk317.relay.model.MsgInfo
import io.github.magisk317.relay.model.Sender
import io.github.magisk317.relay.model.setting.WebhookSetting
import io.github.magisk317.relay.domain.sender.SenderType
import io.github.magisk317.relay.forwarder.utils.sender.WebhookUtils
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import io.github.magisk317.relay.ui.sender.SenderViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.util.Date
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val showMessage: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }
    
    var name by remember { mutableStateOf("") }
    var webServer by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("POST") }
    var webParams by remember { mutableStateOf("") }
    var headersJson by remember { mutableStateOf("") }
    var receiveCode by remember { mutableStateOf(true) }
    var receiveNonCode by remember { mutableStateOf(true) }
    var receiveAppNotify by remember { mutableStateOf(true) }
    var receiveCallNotify by remember { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(false) }
    var currentSender by remember { mutableStateOf<Sender?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    val webhookAddressHint = if (BuildConfig.ALLOW_HTTP_WEBHOOK) {
        "支持 http:// 或 https://"
    } else {
        "当前构建仅支持 https://"
    }

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
                    Gson().fromJson(sender.jsonSetting, WebhookSetting::class.java)
                } catch (@Suppress("SwallowedException") e: com.google.gson.JsonSyntaxException) {
                    null
                }
                if (setting != null) {
                    webServer = setting.webServer
                    secret = setting.secret
                    method = setting.method
                    webParams = setting.webParams
                    headersJson = if (setting.headers.isEmpty()) "" else Gson().toJson(setting.headers)
                }
            }
        }
        isLoaded = true
    }

    fun parseHeadersOrThrow(): Map<String, String> {
        if (headersJson.isBlank()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val raw = Gson().fromJson<Map<String, Any?>>(headersJson, type) ?: emptyMap()
            raw
                .filterKeys { it.isNotBlank() }
                .mapValues { it.value?.toString() ?: "" }
        } catch (_: Exception) {
            throw IllegalArgumentException("请求头 JSON 格式错误，例如 {\"Authorization\":\"Bearer xxx\"}")
        }
    }

    fun buildSender(status: Int): Sender {
        val setting = WebhookSetting(
            method = method,
            webServer = webServer,
            secret = secret,
            webParams = webParams,
            headers = parseHeadersOrThrow()
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
            type = SenderType.WEBHOOK,
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

    fun isWebhookUrlPolicyValid(url: String): Boolean {
        val trimmed = url.trim()
        if (!BuildConfig.ALLOW_HTTP_WEBHOOK && trimmed.startsWith("http://", ignoreCase = true)) {
            showMessage("当前构建版本仅支持 HTTPS Webhook 地址")
            return false
        }
        return true
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
                            showMessage("信息已保存")
                            showExitDialog = false
                            onBack()
                        }
                        .onFailure { e ->
                            showMessage("保存草稿失败: ${e.message}")
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
                title = { Text(if (senderId == 0L) "新增 Webhook" else "编辑 Webhook") },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (!isWebhookUrlPolicyValid(webServer)) return@TextButton
                        coroutineScope.launch {
                            runCatching { viewModel.saveSenderSync(buildSender(status = 1)) }
                                .onSuccess {
                                    showMessage("保存成功")
                                    onBack()
                                }
                                .onFailure { e ->
                                    showMessage("保存失败: ${e.message}")
                                }
                        }
                    }) {
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        if (!isLoaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("通道名称") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = webServer,
                onValueChange = { webServer = it },
                label = { Text("Webhook 完整地址 (必填)") },
                supportingText = { Text(webhookAddressHint) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text("加签密钥 (选填)") },
                modifier = Modifier.fillMaxWidth()
            )
            SingleChoiceSegmentedSelector(
                options = listOf(
                    SegmentedOption("GET", "GET"),
                    SegmentedOption("POST", "POST"),
                ),
                selected = method,
                onSelect = { method = it },
            )
            OutlinedTextField(
                value = webParams,
                onValueChange = { webParams = it },
                label = { Text("自定义 WebParams / JSON 体 (选填)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            OutlinedTextField(
                value = headersJson,
                onValueChange = { headersJson = it },
                label = { Text("自定义 Headers JSON (选填)") },
                supportingText = { Text("例如: {\"Authorization\":\"Bearer xxx\"}") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
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
            SenderTestActionRow(channel = "Webhook") {
                if (!isWebhookUrlPolicyValid(webServer)) return@SenderTestActionRow
                val setting = WebhookSetting(
                    method = method,
                    webServer = webServer,
                    secret = secret,
                    webParams = webParams,
                    headers = parseHeadersOrThrow(),
                )
                val msg = MsgInfo(
                    type = "sms",
                    from = "10086",
                    content = "Webhook 连通性测试消息",
                    date = Date(),
                    simInfo = "SIM1",
                )
                WebhookUtils.sendMsg(setting, msg)
            }
        }
    }
}
