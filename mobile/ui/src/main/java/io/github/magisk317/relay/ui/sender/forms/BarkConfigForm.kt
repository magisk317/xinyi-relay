package io.github.magisk317.relay.ui.sender.forms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.sender.AesUtils
import io.github.magisk317.relay.sender.config.BarkSetting
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.BarkUtils
import io.github.magisk317.relay.ui.sender.getSenderTypeName
import io.github.magisk317.relay.ui.sender.SenderViewModel
import io.github.magisk317.relay.sender.SenderSettingJson
import kotlinx.coroutines.launch
import java.util.Date
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarkConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current


    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val activeScheduleEntry = LocalSenderActiveScheduleEntry.current
    val activeSchedule = activeScheduleEntry?.schedule ?: io.github.magisk317.relay.engine.sender.SenderActiveSchedule()
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var receiveCode by remember { mutableStateOf(true) }
    var receiveNonCode by remember { mutableStateOf(true) }
    var receiveAppNotify by remember { mutableStateOf(true) }
    var receiveCallNotify by remember { mutableStateOf(false) }
    var currentSender by remember { mutableStateOf<Sender?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    // 加密配置
    var encryptionType by remember { mutableStateOf("none") }
    var encryptionKey by remember { mutableStateOf("") }
    var encryptionIv by remember { mutableStateOf("") }
    var showEncryptionSettings by remember { mutableStateOf(false) }

    val encryptionNoneLabel = stringResource(R.string.sender_form_label_bark_encryption_none)
    val encryptionGcmLabel = stringResource(R.string.sender_form_label_bark_encryption_gcm)
    val encryptionCbcLabel = stringResource(R.string.sender_form_label_bark_encryption_cbc)
    val encryptionKeyGeneratedLabel = stringResource(R.string.sender_form_label_bark_encryption_key_generated)
    val encryptionIvGeneratedLabel = stringResource(R.string.sender_form_label_bark_encryption_iv_generated)

    val encryptionOptions = listOf(
        "none" to encryptionNoneLabel,
        "AES/GCM/NoPadding" to encryptionGcmLabel,
        "AES/CBC/PKCS5Padding" to encryptionCbcLabel,
    )

    LaunchedEffect(senderId) {
        if (senderId > 0) {
            viewModel.getSender(senderId)?.let { sender ->
                currentSender = sender
                name = sender.name
                receiveCode = sender.receiveCode == 1
                receiveNonCode = sender.receiveNonCode == 1
                receiveAppNotify = sender.receiveAppNotify == 1
                receiveCallNotify = sender.receiveCallNotify == 1
                runCatching { SenderSettingJson.decode<BarkSetting>(sender.jsonSetting) }.getOrNull()?.let {
                    server = it.server
                    title = it.title
                    encryptionType = it.transformation
                    encryptionKey = it.key
                    encryptionIv = it.iv
                    showEncryptionSettings = it.transformation != "none"
                }
            }
        }
    }

    fun buildSender(status: Int): Sender {
        val setting = BarkSetting(
            server = server,
            title = title,
            transformation = encryptionType,
            key = encryptionKey,
            iv = encryptionIv,
        )
        return currentSender?.copy(
            name = name,
            jsonSetting = SenderSettingJson.encode(setting),
            status = status,
            receiveCode = if (receiveCode) 1 else 0,
            receiveNonCode = if (receiveNonCode) 1 else 0,
            receiveAppNotify = if (receiveAppNotify) 1 else 0,
            receiveCallNotify = if (receiveCallNotify) 1 else 0,
            activeSchedule = activeSchedule,
            time = Date(),
        ) ?: Sender(
            id = 0,
            type = SenderType.BARK,
            name = name,
            jsonSetting = SenderSettingJson.encode(setting),
            status = status,
            receiveCode = if (receiveCode) 1 else 0,
            receiveNonCode = if (receiveNonCode) 1 else 0,
            receiveAppNotify = if (receiveAppNotify) 1 else 0,
            receiveCallNotify = if (receiveCallNotify) 1 else 0,
            activeSchedule = activeSchedule,
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
                            showMessage(context.getString(R.string.sender_form_draft_saved))
                            showExitDialog = false
                            onBack()
                        }
                        .onFailure { showMessage(context.getString(R.string.sender_form_draft_save_failed, it.message ?: it.javaClass.simpleName)) }
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
                            getSenderTypeName(context, SenderType.BARK),
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching { viewModel.saveSenderSync(buildSender(status = 1)) }
                                .onSuccess {
                                    showMessage(context.getString(R.string.sender_form_save_success))
                                    onBack()
                                }
                                .onFailure {
                                    showMessage(
                                        context.getString(
                                            R.string.sender_form_save_failed,
                                            it.message ?: it.javaClass.simpleName,
                                        ),
                                    )
                                }
                        }
                    }) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text(stringResource(R.string.sender_form_name_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                server,
                { server = it },
                label = { Text(stringResource(R.string.sender_form_label_bark_server)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                title,
                { title = it },
                label = { Text(stringResource(R.string.sender_form_title_template_label)) },
                placeholder = { Text(stringResource(R.string.sender_form_title_template_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // 加密设置
            Text(
                text = stringResource(R.string.sender_form_label_bark_encryption),
                style = MaterialTheme.typography.titleMedium,
            )

            // 加密方式选择
            var encryptionExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = encryptionExpanded,
                onExpandedChange = { encryptionExpanded = it },
            ) {
                OutlinedTextField(
                    value = encryptionOptions.find { it.first == encryptionType }?.second
                        ?: stringResource(R.string.sender_form_label_bark_encryption_none),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.sender_form_label_bark_encryption_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = encryptionExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = encryptionExpanded,
                    onDismissRequest = { encryptionExpanded = false },
                ) {
                    encryptionOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                encryptionType = value
                                showEncryptionSettings = value != "none"
                                // 切换到 GCM 模式时清空 IV
                                if (value == "AES/GCM/NoPadding") {
                                    encryptionIv = ""
                                }
                                encryptionExpanded = false
                            },
                        )
                    }
                }
            }

            // 加密密钥和 IV 设置
            if (showEncryptionSettings) {
                Spacer(modifier = Modifier.height(8.dp))

                // 密钥输入
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = encryptionKey,
                        onValueChange = { encryptionKey = it },
                        label = { Text(stringResource(R.string.sender_form_label_bark_encryption_key)) },
                        placeholder = { Text(stringResource(R.string.sender_form_label_bark_encryption_key_hint)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            encryptionKey = AesUtils.generateKey()
                            showMessage(encryptionKeyGeneratedLabel)
                        },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.sender_form_label_bark_encryption_key))
                    }
                }

                // IV 输入（仅 CBC 模式）
                if (encryptionType == "AES/CBC/PKCS5Padding") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = encryptionIv,
                            onValueChange = { encryptionIv = it },
                            label = { Text(stringResource(R.string.sender_form_label_bark_encryption_iv)) },
                            placeholder = { Text(stringResource(R.string.sender_form_label_bark_encryption_iv_hint)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        IconButton(
                            onClick = {
                                encryptionIv = AesUtils.generateIv(encryptionType)
                                showMessage(encryptionIvGeneratedLabel)
                            },
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.sender_form_label_bark_encryption_iv))
                        }
                    }
                }

                // 加密说明
                Text(
                    text = when (encryptionType) {
                        "AES/GCM/NoPadding" -> stringResource(R.string.sender_form_label_bark_encryption_gcm_desc)
                        "AES/CBC/PKCS5Padding" -> stringResource(R.string.sender_form_label_bark_encryption_cbc_desc)
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
            SenderTestActionRow(channel = "Bark") {
                BarkUtils.sendMsg(
                    BarkSetting(
                        server = server,
                        title = title,
                        transformation = encryptionType,
                        key = encryptionKey,
                        iv = encryptionIv,
                    ),
                    buildSenderTestMsgInfo(context, getSenderTypeName(context, SenderType.BARK)),
                )
            }
        }
    }
}
