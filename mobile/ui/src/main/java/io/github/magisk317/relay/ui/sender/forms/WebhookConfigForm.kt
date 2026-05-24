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
import io.github.magisk317.relay.mobileui.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.SenderSettingDrafts
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import io.github.magisk317.relay.ui.sender.getSenderTypeName
import io.github.magisk317.relay.ui.sender.SenderViewModel
import io.github.magisk317.relay.sender.SenderSettingJson
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
    
    val activeScheduleEntry = LocalSenderActiveScheduleEntry.current
    val activeSchedule = activeScheduleEntry?.schedule ?: io.github.magisk317.relay.engine.sender.SenderActiveSchedule()
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
        context.getString(R.string.sender_form_webhook_hint_http_https)
    } else {
        context.getString(R.string.sender_form_webhook_hint_https_only)
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
                val draft = SenderSettingDrafts.fromSender(sender)
                webServer = draft.string("webServer")
                secret = draft.string("secret")
                method = draft.string("method").ifBlank { "POST" }
                webParams = draft.string("webParams")
                val headers = draft.stringMap("headers")
                headersJson = if (headers.isEmpty()) "" else SenderSettingJson.encodeStringMap(headers)
            }
        }
        isLoaded = true
    }

    fun parseHeadersOrThrow(): Map<String, String> {
        if (headersJson.isBlank()) return emptyMap()
        return SenderSettingJson.decodeStringMapLenientOrNull(headersJson)
            ?: run {
                throw IllegalArgumentException("Invalid headers JSON, e.g. {\"Authorization\":\"Bearer xxx\"}")
            }
    }

    fun buildSender(status: Int): Sender {
        val json = SenderSettingDrafts.empty(SenderType.WEBHOOK)
            .withString("method", method)
            .withString("webServer", webServer)
            .withString("secret", secret)
            .withString("webParams", webParams)
            .withStringMap("headers", parseHeadersOrThrow())
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
            activeSchedule = activeSchedule,
            time = Date()
        )
    }

    fun isWebhookUrlPolicyValid(url: String): Boolean {
        val trimmed = url.trim()
        if (!BuildConfig.ALLOW_HTTP_WEBHOOK && trimmed.startsWith("http://", ignoreCase = true)) {
            showMessage(context.getString(R.string.sender_form_https_only_webhook))
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
                            getSenderTypeName(context, SenderType.WEBHOOK),
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
                        if (!isWebhookUrlPolicyValid(webServer)) return@TextButton
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
                    }) {
                        Text(stringResource(R.string.save))
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
                label = { Text(stringResource(R.string.sender_form_name_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = webServer,
                onValueChange = { webServer = it },
                label = { Text(stringResource(R.string.sender_form_label_full_webhook_url_required)) },
                supportingText = { Text(webhookAddressHint) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text(stringResource(R.string.sender_form_label_signature_secret_optional)) },
                modifier = Modifier.fillMaxWidth()
            )
            SingleChoiceSegmentedSelector(
                options = listOf(
                    SegmentedOption("GET", stringResource(R.string.sender_segment_get)),
                    SegmentedOption("POST", stringResource(R.string.sender_segment_post)),
                ),
                selected = method,
                onSelect = { method = it },
            )
            OutlinedTextField(
                value = webParams,
                onValueChange = { webParams = it },
                label = { Text(stringResource(R.string.sender_form_label_custom_webparams_optional)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            OutlinedTextField(
                value = headersJson,
                onValueChange = { headersJson = it },
                label = { Text(stringResource(R.string.sender_form_label_custom_headers_json_optional)) },
                supportingText = { Text(stringResource(R.string.sender_form_label_custom_headers_json_example)) },
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
                activeSchedule = activeSchedule,
                onActiveScheduleChange = { activeScheduleEntry?.onChange(it) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            SenderTestActionRow(channel = "Webhook") {
                if (isWebhookUrlPolicyValid(webServer)) {
                    viewModel.sendConfiguredSenderTest(
                        context = context,
                        senderType = SenderType.WEBHOOK,
                        sender = buildSender(status = 1),
                    )
                }
            }
        }
    }
}
