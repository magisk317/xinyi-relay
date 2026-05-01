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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.sender.config.EmailSetting
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.sender.EmailUtils
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.ui.sender.getSenderTypeName
import io.github.magisk317.relay.ui.sender.SenderViewModel
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.Date
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailConfigForm(senderId: Long, onBack: () -> Unit, viewModel: SenderViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current


    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val activeScheduleEntry = LocalSenderActiveScheduleEntry.current
    val activeSchedule = activeScheduleEntry?.schedule ?: io.github.magisk317.relay.engine.sender.SenderActiveSchedule()
    var name by remember { mutableStateOf("") }
    var mailType by remember { mutableStateOf("") }
    var authEmail by remember { mutableStateOf("") }
    var fromEmail by remember { mutableStateOf("") }
    var fromEmailAlias by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("465") }
    var toEmail by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var ssl by remember { mutableStateOf(true) }
    var startTls by remember { mutableStateOf(false) }
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
                runCatching { Gson().fromJson(sender.jsonSetting, EmailSetting::class.java) }.getOrNull()?.let {
                    mailType = it.mailType
                    authEmail = it.authEmail.ifBlank { it.fromEmail }
                    fromEmail = it.fromEmail
                    fromEmailAlias = it.fromEmailAlias.ifBlank { it.nickname }
                    pwd = it.pwd
                    host = it.host
                    port = it.port
                    toEmail = it.toEmail
                    title = it.title
                    ssl = it.ssl
                    startTls = it.startTls
                }
            }
        }
    }

    fun buildSender(status: Int): Sender {
        val setting = EmailSetting(
            mailType = mailType,
            authEmail = authEmail,
            fromEmail = fromEmail,
            fromEmailAlias = fromEmailAlias,
            pwd = pwd,
            host = host,
            port = port,
            ssl = ssl,
            startTls = startTls,
            toEmail = toEmail,
            title = title,
        )
        return currentSender?.copy(
            name = name,
            jsonSetting = Gson().toJson(setting),
            status = status,
            receiveCode = if (receiveCode) 1 else 0,
            receiveNonCode = if (receiveNonCode) 1 else 0,
            receiveAppNotify = if (receiveAppNotify) 1 else 0,
            receiveCallNotify = if (receiveCallNotify) 1 else 0,
            activeSchedule = activeSchedule,
            time = Date(),
        ) ?: Sender(
            id = 0,
            type = SenderType.EMAIL,
            name = name,
            jsonSetting = Gson().toJson(setting),
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
                        .onFailure {
                            showMessage(
                                context.getString(
                                    R.string.sender_form_draft_save_failed,
                                    it.message ?: it.javaClass.simpleName,
                                ),
                            )
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
                            getSenderTypeName(context, SenderType.EMAIL),
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back),
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
                mailType,
                { mailType = it },
                label = { Text(stringResource(R.string.sender_form_label_mail_type_example)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                authEmail,
                { authEmail = it },
                label = { Text(stringResource(R.string.sender_form_label_auth_email)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                fromEmail,
                { fromEmail = it },
                label = { Text(stringResource(R.string.sender_form_label_from_email)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                fromEmailAlias,
                { fromEmailAlias = it },
                label = { Text(stringResource(R.string.sender_form_label_from_email_alias)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                pwd,
                { pwd = it },
                label = { Text(stringResource(R.string.sender_form_label_auth_code_or_password)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                host,
                { host = it },
                label = { Text(stringResource(R.string.sender_form_label_smtp_host)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                port,
                { port = it },
                label = { Text(stringResource(R.string.sender_form_label_smtp_port)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                toEmail,
                { toEmail = it },
                label = { Text(stringResource(R.string.sender_form_label_recipients_comma)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                title,
                { title = it },
                label = { Text(stringResource(R.string.sender_form_label_title)) },
                placeholder = { Text(stringResource(R.string.sender_form_title_template_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.sender_segment_ssl))
                Switch(checked = ssl, onCheckedChange = { ssl = it })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.sender_segment_starttls))
                Switch(checked = startTls, onCheckedChange = { startTls = it })
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
            SenderTestActionRow(channel = "Email") {
                EmailUtils.sendMsg(
                    EmailSetting(
                        mailType = mailType,
                        authEmail = authEmail,
                        fromEmail = fromEmail,
                        fromEmailAlias = fromEmailAlias,
                        pwd = pwd,
                        host = host,
                        port = port,
                        ssl = ssl,
                        startTls = startTls,
                        toEmail = toEmail,
                        title = title,
                    ),
                    buildSenderTestMsgInfo(context, getSenderTypeName(context, SenderType.EMAIL)),
                )
            }
        }
    }
}
