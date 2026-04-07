@file:Suppress("LocalContextGetResourceValueCall", "NonObservableLocale")

package io.github.magisk317.relay.ui.sender

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.core.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.data.repository.ForwardTypeGateSnapshot
import io.github.magisk317.relay.data.repository.ForwardTypeGateUpdate
import io.github.magisk317.relay.data.repository.MessageTypeGateSnapshot
import io.github.magisk317.relay.data.repository.MessageTypeGateUpdate
import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.data.repository.SimRemarkSettingsSnapshot
import io.github.magisk317.relay.domain.pipeline.ForwardCommonConfigStore
import io.github.magisk317.relay.domain.sender.SenderType
import io.github.magisk317.relay.domain.model.ForwardCommonConfig
import io.github.magisk317.relay.domain.model.Sender
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject

private data class TemplateVariable(
    val labelRes: Int,
    val token: String,
)

private val forwardTemplateVariables = listOf(
    TemplateVariable(R.string.sender_template_var_sender, "{{FROM}}"),
    TemplateVariable(R.string.sender_template_var_sms_body, "{{SMS}}"),
    TemplateVariable(R.string.sender_template_var_call_type, "{{CALL_TYPE}}"),
    TemplateVariable(R.string.sender_template_var_sim_note, "{{CARD_SLOT}}"),
    TemplateVariable(R.string.sender_template_var_sim_sub_id, "{{CARD_SUBID}}"),
    TemplateVariable(R.string.sender_template_var_contact_name, "{{CONTACT_NAME}}"),
    TemplateVariable(R.string.sender_template_var_phone_area, "{{PHONE_AREA}}"),
    TemplateVariable(R.string.sender_template_var_app_package, "{{PACKAGE_NAME}}"),
    TemplateVariable(R.string.sender_template_var_app_name, "{{APP_NAME}}"),
    TemplateVariable(R.string.sender_template_var_notification_body, "{{MSG}}"),
    TemplateVariable(R.string.sender_template_var_battery_percent, "{{BATTERY_PCT}}"),
    TemplateVariable(R.string.sender_template_var_battery_status, "{{BATTERY_STATUS}}"),
    TemplateVariable(R.string.sender_template_var_charging_source, "{{BATTERY_PLUGGED}}"),
    TemplateVariable(R.string.sender_template_var_battery_info, "{{BATTERY_INFO}}"),
    TemplateVariable(R.string.sender_template_var_battery_info_brief, "{{BATTERY_INFO_SIMPLE}}"),
    TemplateVariable(R.string.sender_template_var_public_ipv4, "{{IPV4}}"),
    TemplateVariable(R.string.sender_template_var_public_ipv6, "{{IPV6}}"),
    TemplateVariable(R.string.sender_template_var_ip_list, "{{IP_LIST}}"),
    TemplateVariable(R.string.sender_template_var_network_state, "{{NET_TYPE}}"),
    TemplateVariable(R.string.sender_template_var_received_at, "{{RECEIVE_TIME}}"),
    TemplateVariable(R.string.sender_template_var_current_time, "{{CURRENT_TIME}}"),
    TemplateVariable(R.string.sender_template_var_device_name, "{{DEVICE_NAME}}"),
    TemplateVariable(R.string.sender_template_var_app_version, "{{APP_VERSION}}"),
)
private val templateTokenRegex = Regex("\\{\\{[^{}]+\\}\\}")

private fun toAppNotifyTemplate(template: String): String =
    ForwardCommonConfigStore.adaptTemplateForMessageType(template, MessageType.APP_NOTIFY)

private fun toCallNotifyTemplate(template: String): String =
    ForwardCommonConfigStore.adaptTemplateForMessageType(
        template.replace("{{SMS}}", "{{CALL_TYPE}} {{SMS}}"),
        MessageType.CALL_NOTIFY,
    )

private fun appNotifyDefaultTemplate(): String = toAppNotifyTemplate(ForwardCommonConfigStore.defaultTemplate())
private fun appNotifyFullTemplate(): String = toAppNotifyTemplate(ForwardCommonConfigStore.fullInfoTemplate())
private fun callNotifyDefaultTemplate(): String = toCallNotifyTemplate(ForwardCommonConfigStore.defaultTemplate())
private fun callNotifyFullTemplate(): String = toCallNotifyTemplate(ForwardCommonConfigStore.fullInfoTemplate())

private val appNotifyTemplateVariables = forwardTemplateVariables.map { variable ->
    when (variable.token) {
        "{{CARD_SLOT}}" -> variable.copy(labelRes = R.string.sender_template_var_app_note)
        "{{CARD_SUBID}}" -> variable.copy(labelRes = R.string.sender_template_var_app_key)
        else -> variable
    }
}

private val callNotifyTemplateVariables = forwardTemplateVariables.map { variable ->
    when (variable.token) {
        "{{SMS}}" -> variable.copy(labelRes = R.string.sender_template_var_call_details)
        "{{CARD_SLOT}}" -> variable.copy(labelRes = R.string.sender_template_var_call_source)
        "{{CARD_SUBID}}" -> variable.copy(labelRes = R.string.sender_template_var_call_key)
        else -> variable
    }
}
private const val DIALOG_WIDTH_FRACTION = 0.92f
private const val UNDO_SNACKBAR_DURATION_MS = 5_000L
private const val UNDO_COUNTDOWN_TICK_MS = 50L

private fun senderTypeGroupLabel(context: android.content.Context, key: String): String {
    return when (key) {
        "collaboration" -> context.getString(R.string.sender_group_collaboration)
        "push" -> context.getString(R.string.sender_group_push)
        else -> context.getString(R.string.sender_group_other)
    }
}

private fun buildSmsPreviewMessage(context: android.content.Context): io.github.magisk317.relay.domain.model.MsgInfo {
    return io.github.magisk317.relay.domain.model.MsgInfo(
        type = "sms",
        from = context.getString(R.string.sender_preview_sms_from),
        content = context.getString(R.string.sender_preview_sms_content),
        date = Date(),
        simInfo = context.getString(R.string.sender_preview_sms_sim_info),
        simSlot = 0,
        subId = 1,
        contactName = context.getString(R.string.sender_preview_sms_contact_name),
        phoneArea = context.getString(R.string.sender_preview_sms_phone_area),
    )
}

private fun buildAppNotifyPreviewMessage(context: android.content.Context): io.github.magisk317.relay.domain.model.MsgInfo {
    return io.github.magisk317.relay.domain.model.MsgInfo(
        type = "app_notify",
        from = context.getString(R.string.sender_preview_app_from),
        content = context.getString(R.string.sender_preview_app_content),
        date = Date(),
        simInfo = context.getString(R.string.sender_preview_app_sim_info),
        packageName = "com.tencent.mm",
        appName = context.getString(R.string.sender_preview_app_name),
        title = context.getString(R.string.sender_preview_app_title),
        message = context.getString(R.string.sender_preview_app_message),
        contactName = context.getString(R.string.sender_preview_app_contact_name),
    )
}

private fun buildCallNotifyPreviewMessage(context: android.content.Context): io.github.magisk317.relay.domain.model.MsgInfo {
    return io.github.magisk317.relay.domain.model.MsgInfo(
        type = "call_notify",
        from = "10086",
        content = context.getString(R.string.sender_preview_call_content),
        date = Date(),
        simInfo = context.getString(R.string.sender_preview_call_sim_info),
        simSlot = 0,
        subId = 42,
        callType = 3,
        contactName = context.getString(R.string.sender_preview_call_contact_name),
        phoneArea = context.getString(R.string.sender_preview_call_phone_area),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderListScreen(
    viewModel: SenderViewModel = koinViewModel(),
    onAddClick: (Int) -> Unit,
    onEditClick: (Long) -> Unit,
    forceShowTypeDialog: Boolean = false,
    onForceShowHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository: SettingsRepository = koinInject()
    val snackbarHostState = remember { SnackbarHostState() }
    val senders by viewModel.senderList.collectAsStateWithLifecycle()
    val commonConfig by viewModel.forwardCommonConfig.collectAsStateWithLifecycle()
    val appNotifyTemplate by viewModel.appNotifyTemplate.collectAsStateWithLifecycle()
    val callNotifyTemplate by viewModel.callNotifyTemplate.collectAsStateWithLifecycle()
    val simRemarkSettings by viewModel.simRemarkSettings.collectAsStateWithLifecycle()
    var messageTypeGates by remember { mutableStateOf<MessageTypeGateSnapshot?>(null) }
    var forwardTypeGates by remember { mutableStateOf<ForwardTypeGateSnapshot?>(null) }
    var showTypeDialog by remember { mutableStateOf(false) }
    var showGeneralConfigDialog by remember { mutableStateOf(false) }
    var showCommonConfigDialog by remember { mutableStateOf(false) }
    var showAppNotifyConfigDialog by remember { mutableStateOf(false) }
    var showCallNotifyConfigDialog by remember { mutableStateOf(false) }
    var simSlot1Remark by remember { mutableStateOf("") }
    var simSlot2Remark by remember { mutableStateOf("") }
    val messageGateSnapshot = messageTypeGates ?: MessageTypeGateSnapshot(
        smsCodeEnabled = true,
        smsPlainEnabled = true,
        appNotifyEnabled = true,
        callNotifyEnabled = false,
    )
    val forwardGateSnapshot = forwardTypeGates ?: ForwardTypeGateSnapshot(
        smsCodeEnabled = true,
        smsPlainEnabled = true,
        appNotifyEnabled = true,
        callNotifyEnabled = false,
        callNotifyFinalEnabled = false,
    )
    val updateMessageGate: (MessageTypeGateUpdate) -> Unit = { update ->
        scope.launch {
            messageTypeGates = settingsRepository.updateMessageTypeGates(update)
        }
    }
    val updateForwardGate: (ForwardTypeGateUpdate) -> Unit = { update ->
        scope.launch {
            forwardTypeGates = settingsRepository.updateForwardTypeGates(update)
        }
    }
    LaunchedEffect(simRemarkSettings) {
        simSlot1Remark = simRemarkSettings.simSlot1Remark
        simSlot2Remark = simRemarkSettings.simSlot2Remark
    }
    LaunchedEffect(Unit) {
        messageTypeGates = settingsRepository.getMessageTypeGates()
        forwardTypeGates = settingsRepository.getForwardTypeGates()
    }
    LaunchedEffect(forceShowTypeDialog) {
        if (forceShowTypeDialog) {
            showTypeDialog = true
            onForceShowHandled()
        }
    }

    if (showTypeDialog) {
        val otherChannels = mutableListOf(
            SenderType.EMAIL to getSenderTypeName(context, SenderType.EMAIL),
            SenderType.URL_SCHEME to getSenderTypeName(context, SenderType.URL_SCHEME),
            SenderType.SOCKET to getSenderTypeName(context, SenderType.SOCKET),
        )
        if (BuildConfig.ENABLE_SMS_CHANNEL) {
            otherChannels.add(1, SenderType.SMS to getSenderTypeName(context, SenderType.SMS))
        }
        val supportedTypeGroups = listOf(
            senderTypeGroupLabel(context, "collaboration") to listOf(
                SenderType.DINGTALK_GROUP_ROBOT to getSenderTypeName(context, SenderType.DINGTALK_GROUP_ROBOT),
                SenderType.DINGTALK_INNER_ROBOT to getSenderTypeName(context, SenderType.DINGTALK_INNER_ROBOT),
                SenderType.FEISHU to getSenderTypeName(context, SenderType.FEISHU),
                SenderType.FEISHU_APP to getSenderTypeName(context, SenderType.FEISHU_APP),
                SenderType.WEWORK_ROBOT to getSenderTypeName(context, SenderType.WEWORK_ROBOT),
                SenderType.WEWORK_AGENT to getSenderTypeName(context, SenderType.WEWORK_AGENT),
            ),
            senderTypeGroupLabel(context, "push") to listOf(
                SenderType.TELEGRAM to getSenderTypeName(context, SenderType.TELEGRAM),
                SenderType.WEBHOOK to getSenderTypeName(context, SenderType.WEBHOOK),
                SenderType.SERVERCHAN to getSenderTypeName(context, SenderType.SERVERCHAN),
                SenderType.PUSHPLUS to getSenderTypeName(context, SenderType.PUSHPLUS),
                SenderType.GOTIFY to getSenderTypeName(context, SenderType.GOTIFY),
                SenderType.NTFY to getSenderTypeName(context, SenderType.NTFY),
                SenderType.BARK to getSenderTypeName(context, SenderType.BARK),
            ),
            senderTypeGroupLabel(context, "other") to listOf(
                *otherChannels.toTypedArray(),
            ),
        )

        @Suppress("MagicNumber")
        AlertDialog(
            modifier = Modifier.fillMaxWidth(0.88f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showTypeDialog = false },
            title = { Text(stringResource(R.string.sender_add_type_title)) },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    supportedTypeGroups.forEach { (groupName, groupItems) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = groupName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                        }
                        groupItems.forEach { (type, name) ->
                            item {
                                Button(
                                    onClick = {
                                        showTypeDialog = false
                                        onAddClick(type)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(name)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTypeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCommonConfigDialog) {
        ForwardCommonConfigDialog(
            currentConfig = commonConfig,
            simRemarkSettings = simRemarkSettings,
            smsCodeEnabled = messageGateSnapshot.smsCodeEnabled,
            smsPlainEnabled = messageGateSnapshot.smsPlainEnabled,
            onSmsCodeToggle = { enabled ->
                updateMessageGate(MessageTypeGateUpdate(smsCodeEnabled = enabled))
            },
            onSmsPlainToggle = { enabled ->
                updateMessageGate(MessageTypeGateUpdate(smsPlainEnabled = enabled))
            },
            forwardSmsCodeEnabled = forwardGateSnapshot.smsCodeEnabled,
            forwardSmsPlainEnabled = forwardGateSnapshot.smsPlainEnabled,
            onForwardSmsCodeToggle = { enabled ->
                updateForwardGate(ForwardTypeGateUpdate(smsCodeEnabled = enabled))
            },
            onForwardSmsPlainToggle = { enabled ->
                updateForwardGate(ForwardTypeGateUpdate(smsPlainEnabled = enabled))
            },
            onDismiss = { showCommonConfigDialog = false },
            onSave = {
                viewModel.saveForwardCommonConfig(it)
                showCommonConfigDialog = false
            },
        )
    }
    if (showGeneralConfigDialog) {
        GeneralConfigDialog(
            currentDeviceName = commonConfig.deviceName,
            currentSimSlot1Remark = simSlot1Remark,
            currentSimSlot2Remark = simSlot2Remark,
            onDismiss = { showGeneralConfigDialog = false },
            onSave = { deviceName, sim1Remark, sim2Remark ->
                viewModel.saveForwardCommonConfig(commonConfig.copy(deviceName = deviceName))
                viewModel.saveSimRemarkSettings(sim1Remark, sim2Remark)
                showGeneralConfigDialog = false
            },
        )
    }
    if (showAppNotifyConfigDialog) {
        AppNotifyTemplateDialog(
            currentTemplate = appNotifyTemplate,
            currentCommonConfig = commonConfig,
            simRemarkSettings = simRemarkSettings,
            appNotifyEnabled = messageGateSnapshot.appNotifyEnabled,
            onAppNotifyToggle = { enabled ->
                updateMessageGate(MessageTypeGateUpdate(appNotifyEnabled = enabled))
            },
            forwardAppNotifyEnabled = forwardGateSnapshot.appNotifyEnabled,
            onForwardAppNotifyToggle = { enabled ->
                updateForwardGate(ForwardTypeGateUpdate(appNotifyEnabled = enabled))
            },
            onDismiss = { showAppNotifyConfigDialog = false },
            onSave = {
                viewModel.saveAppNotifyTemplate(it)
                showAppNotifyConfigDialog = false
            },
        )
    }
    if (showCallNotifyConfigDialog) {
        CallNotifyTemplateDialog(
            currentTemplate = callNotifyTemplate,
            currentCommonConfig = commonConfig,
            simRemarkSettings = simRemarkSettings,
            callNotifyEnabled = messageGateSnapshot.callNotifyEnabled,
            onCallNotifyToggle = { enabled ->
                updateMessageGate(MessageTypeGateUpdate(callNotifyEnabled = enabled))
            },
            forwardCallNotifyEnabled = forwardGateSnapshot.callNotifyEnabled,
            onForwardCallNotifyToggle = { enabled ->
                updateForwardGate(ForwardTypeGateUpdate(callNotifyEnabled = enabled))
            },
            forwardCallNotifyFinalEnabled = forwardGateSnapshot.callNotifyFinalEnabled,
            onForwardCallNotifyFinalToggle = { enabled ->
                updateForwardGate(ForwardTypeGateUpdate(callNotifyFinalEnabled = enabled))
            },
            onDismiss = { showCallNotifyConfigDialog = false },
            onSave = {
                viewModel.saveCallNotifyTemplate(it)
                showCallNotifyConfigDialog = false
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.sender_config_title)) }) },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 56.dp),
                onClick = { showTypeDialog = true },
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.sender_add_sender_content_description))
            }
        }
    ) { paddingValues ->
        val listBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "top_config_cards") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GeneralConfigCard(
                                modifier = Modifier.weight(1f),
                                deviceName = commonConfig.deviceName,
                                simSlot1Remark = simSlot1Remark,
                                simSlot2Remark = simSlot2Remark,
                                onEdit = { showGeneralConfigDialog = true },
                            )
                            SmsConfigCard(
                                modifier = Modifier.weight(1f),
                                onEdit = { showCommonConfigDialog = true },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppNotifyConfigCard(
                                modifier = Modifier.weight(1f),
                                onEdit = { showAppNotifyConfigDialog = true },
                            )
                            CallNotifyConfigCard(
                                modifier = Modifier.weight(1f),
                                onEdit = { showCallNotifyConfigDialog = true },
                            )
                        }
                    }
                }

                if (senders.isEmpty()) {
                    item(key = "no_sender_hint") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.sender_empty_message))
                        }
                    }
                } else {
                    items(senders, key = { it.id }) { sender ->
                        SenderCard(
                            sender = sender,
                            onEdit = { onEditClick(sender.id) },
                            onToggle = { enabled ->
                                if (enabled) {
                                    val result = viewModel.validateSenderForEnable(sender)
                                    if (!result.valid) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.sender_enable_failed, result.message),
                                            )
                                        }
                                    } else {
                                        viewModel.toggleSenderStatus(sender, enabled)
                                        scope.launch {
                                            snackbarHostState.showSnackbar(context.getString(R.string.pref_sync_snackbar))
                                        }
                                    }
                                } else {
                                    viewModel.toggleSenderStatus(sender, enabled)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.pref_sync_snackbar))
                                    }
                                }
                            },
                            onDelete = {
                                val removedSender = sender.copy()
                                viewModel.deleteSender(removedSender)
                                scope.launch {
                                    val resultDeferred = async {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(
                                                R.string.sender_removed_with_undo,
                                                removedSender.name.ifBlank { getSenderTypeName(context, removedSender.type) },
                                            ),
                                            actionLabel = context.getString(R.string.revoke),
                                            duration = SnackbarDuration.Indefinite,
                                        )
                                    }
                                    delay(UNDO_SNACKBAR_DURATION_MS)
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = runCatching { resultDeferred.await() }.getOrNull()
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreSender(removedSender)
                                    }
                                }
                            }
                        )
                    }
                }
            }
            io.github.magisk317.relay.ui.common.DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                snackbar = { data ->
                    UndoCountdownSnackbar(
                        data = data,
                        totalDurationMs = UNDO_SNACKBAR_DURATION_MS,
                    )
                },
            )
        }
    }
}

@Composable
private fun UndoCountdownSnackbar(
    data: SnackbarData,
    totalDurationMs: Long,
) {
    val startTimeMs = remember(data) { SystemClock.elapsedRealtime() }
    var nowMs by remember(data) { mutableLongStateOf(startTimeMs) }

    LaunchedEffect(data) {
        while (isActive) {
            nowMs = SystemClock.elapsedRealtime()
            delay(UNDO_COUNTDOWN_TICK_MS)
        }
    }

    val elapsedMs = (nowMs - startTimeMs).coerceIn(0L, totalDurationMs)
    val remainingMs = (totalDurationMs - elapsedMs).coerceAtLeast(0L)
    val progress = if (totalDurationMs <= 0L) {
        0f
    } else {
        (remainingMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    }
    val remainingSeconds = ceil(remainingMs / 1000f).toInt().coerceAtLeast(0)

    val hasAction = data.visuals.actionLabel != null
    Snackbar(
        action = {
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) {
                    Text(label)
                }
            }
        },
        dismissAction = if (hasAction) {
            {
                CountdownCircle(
                    progress = progress,
                    seconds = remainingSeconds,
                )
            }
        } else {
            null
        },
    ) {
        Text(data.visuals.message)
    }
}

@Composable
private fun CountdownCircle(
    progress: Float,
    seconds: Int,
) {
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = seconds.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun GeneralConfigCard(
    modifier: Modifier = Modifier,
    deviceName: String,
    simSlot1Remark: String,
    simSlot2Remark: String,
    onEdit: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onEdit),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.sender_general_config_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                text = stringResource(
                    R.string.sender_general_config_summary,
                    deviceName.ifBlank { stringResource(R.string.sender_system_default) },
                    simSlot1Remark.ifBlank { stringResource(R.string.sender_not_set) },
                    simSlot2Remark.ifBlank { stringResource(R.string.sender_not_set) },
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SmsConfigCard(
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onEdit),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.sender_sms_config_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.sender_config_card_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppNotifyConfigCard(
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onEdit),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.sender_app_config_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.sender_config_card_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CallNotifyConfigCard(
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onEdit),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.sender_call_config_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.sender_config_card_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfigGateToggle(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun GeneralConfigDialog(
    currentDeviceName: String,
    currentSimSlot1Remark: String,
    currentSimSlot2Remark: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var deviceName by remember(currentDeviceName) { mutableStateOf(currentDeviceName) }
    var simSlot1Remark by remember(currentSimSlot1Remark) { mutableStateOf(currentSimSlot1Remark) }
    var simSlot2Remark by remember(currentSimSlot2Remark) { mutableStateOf(currentSimSlot2Remark) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sender_general_config_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sender_dialog_device_name_label)) },
                    placeholder = { Text(stringResource(R.string.sender_dialog_device_name_placeholder)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = simSlot1Remark,
                    onValueChange = { simSlot1Remark = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sender_dialog_sim1_note_label)) },
                    placeholder = { Text(stringResource(R.string.sender_dialog_sim_note_placeholder)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = simSlot2Remark,
                    onValueChange = { simSlot2Remark = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sender_dialog_sim2_note_label)) },
                    placeholder = { Text(stringResource(R.string.sender_dialog_sim_note_placeholder)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        deviceName.trim(),
                        simSlot1Remark.trim(),
                        simSlot2Remark.trim(),
                    )
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ForwardCommonConfigDialog(
    currentConfig: ForwardCommonConfig,
    simRemarkSettings: SimRemarkSettingsSnapshot,
    smsCodeEnabled: Boolean,
    smsPlainEnabled: Boolean,
    onSmsCodeToggle: (Boolean) -> Unit,
    onSmsPlainToggle: (Boolean) -> Unit,
    forwardSmsCodeEnabled: Boolean,
    forwardSmsPlainEnabled: Boolean,
    onForwardSmsCodeToggle: (Boolean) -> Unit,
    onForwardSmsPlainToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ForwardCommonConfig) -> Unit,
) {
    val context = LocalContext.current
    var templateValue by remember(currentConfig.messageTemplate) {
        mutableStateOf(TextFieldValue(currentConfig.messageTemplate))
    }
    var templateFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val fillTemplateInteractionSource = remember { MutableInteractionSource() }
    val isFillTemplatePressed by fillTemplateInteractionSource.collectIsPressedAsState()
    var suppressNextClick by remember { mutableStateOf(false) }
    fun renderPreview(templateText: String): String {
        val previewConfig = currentConfig.copy(messageTemplate = templateText)
        return ForwardCommonConfigStore.applyToMessage(
            context = context,
            messageType = MessageType.SMS_PLAIN,
            msgInfo = buildSmsPreviewMessage(context),
            config = previewConfig,
            simRemarkSnapshot = simRemarkSettings,
        ).content
    }
    var previewText by remember(currentConfig.deviceName, currentConfig.messageTemplate) {
        mutableStateOf(renderPreview(templateValue.text))
    }
    fun insertToken(token: String) {
        val start = templateValue.selection.start.coerceIn(0, templateValue.text.length)
        val end = templateValue.selection.end.coerceIn(0, templateValue.text.length)
        val newText = buildString {
            append(templateValue.text.substring(0, start))
            append(token)
            append(templateValue.text.substring(end))
        }
        val cursor = start + token.length
        templateValue = templateValue.copy(text = newText, selection = TextRange(cursor))
        if (!templateFocused) {
            previewText = renderPreview(templateValue.text)
        }
    }

    fun normalizeTokenDeletion(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
        val oldText = oldValue.text
        val newText = newValue.text
        val oldSelection = oldValue.selection
        val newSelection = newValue.selection
        if (oldSelection.start != oldSelection.end) return newValue
        if (newText.length != oldText.length - 1) return newValue

        val oldCursor = oldSelection.start
        val isBackspace = newSelection.start == (oldCursor - 1).coerceAtLeast(0)
        val removeIndex = if (isBackspace) oldCursor - 1 else oldCursor
        if (removeIndex !in oldText.indices) return newValue

        val token = templateTokenRegex.findAll(oldText).firstOrNull { match ->
            removeIndex in match.range
        } ?: return newValue

        val start = token.range.first
        val endExclusive = token.range.last + 1
        val merged = oldText.removeRange(start, endExclusive)
        return TextFieldValue(
            text = merged,
            selection = TextRange(start.coerceAtMost(merged.length)),
        )
    }

    @Suppress("MagicNumber")
    LaunchedEffect(isFillTemplatePressed) {
        if (isFillTemplatePressed) {
            delay(10_000)
            if (isFillTemplatePressed) {
                suppressNextClick = true
                val fullTemplate = ForwardCommonConfigStore.fullInfoTemplate()
                templateValue = TextFieldValue(
                    fullTemplate,
                    selection = TextRange(fullTemplate.length),
                )
            }
        }
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_FRACTION),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sender_sms_config_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.sender_gate_ingress_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.sender_gate_ingress_summary_sms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_msg_type_sms_code_title),
                    summary = stringResource(id = R.string.pref_msg_type_sms_code_summary),
                    checked = smsCodeEnabled,
                    onCheckedChange = onSmsCodeToggle,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_msg_type_sms_plain_title),
                    summary = stringResource(id = R.string.pref_msg_type_sms_plain_summary),
                    checked = smsPlainEnabled,
                    onCheckedChange = onSmsPlainToggle,
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.sender_gate_forwarding_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_forward_sms_code_title),
                    summary = stringResource(id = R.string.pref_forward_sms_code_summary),
                    checked = forwardSmsCodeEnabled,
                    onCheckedChange = onForwardSmsCodeToggle,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_forward_sms_plain_title),
                    summary = stringResource(id = R.string.pref_forward_sms_plain_summary),
                    checked = forwardSmsPlainEnabled,
                    onCheckedChange = onForwardSmsPlainToggle,
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = templateValue,
                    onValueChange = { newValue ->
                        templateValue = normalizeTokenDeletion(templateValue, newValue)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .onFocusChanged { focusState ->
                            if (templateFocused && !focusState.isFocused) {
                                previewText = renderPreview(templateValue.text)
                            }
                            templateFocused = focusState.isFocused
                        },
                    label = { Text(stringResource(R.string.sender_template_sms_label)) },
                    placeholder = { Text(stringResource(R.string.sender_template_placeholder)) },
                    supportingText = { Text(stringResource(R.string.sender_template_supporting)) },
                )
                Text(
                    text = stringResource(R.string.sender_template_preview, previewText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            if (suppressNextClick) {
                                suppressNextClick = false
                                return@TextButton
                            }
                            val defaultTemplate = ForwardCommonConfigStore.defaultTemplate()
                            templateValue = TextFieldValue(
                                defaultTemplate,
                                selection = TextRange(defaultTemplate.length),
                            )
                            previewText = renderPreview(templateValue.text)
                        },
                        interactionSource = fillTemplateInteractionSource,
                    ) {
                        Text(stringResource(R.string.sender_template_fill_default))
                    }
                }
                HorizontalDivider()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(forwardTemplateVariables.size) { index ->
                        val variable = forwardTemplateVariables[index]
                        OutlinedButton(
                            onClick = { insertToken(variable.token) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(stringResource(variable.labelRes), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        currentConfig.copy(
                            messageTemplate = templateValue.text,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun AppNotifyTemplateDialog(
    currentTemplate: String,
    currentCommonConfig: ForwardCommonConfig,
    simRemarkSettings: SimRemarkSettingsSnapshot,
    appNotifyEnabled: Boolean,
    onAppNotifyToggle: (Boolean) -> Unit,
    forwardAppNotifyEnabled: Boolean,
    onForwardAppNotifyToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    var templateValue by remember(currentTemplate) { mutableStateOf(TextFieldValue(currentTemplate)) }
    var templateFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val fillTemplateInteractionSource = remember { MutableInteractionSource() }
    val isFillTemplatePressed by fillTemplateInteractionSource.collectIsPressedAsState()
    var suppressNextClick by remember { mutableStateOf(false) }
    fun renderPreview(templateText: String): String {
        val previewConfig = currentCommonConfig.copy(messageTemplate = templateText)
        return ForwardCommonConfigStore.applyToMessage(
            context = context,
            messageType = MessageType.APP_NOTIFY,
            msgInfo = buildAppNotifyPreviewMessage(context),
            config = previewConfig,
            simRemarkSnapshot = simRemarkSettings,
        ).content
    }
    var previewText by remember(currentTemplate, currentCommonConfig.deviceName) {
        mutableStateOf(renderPreview(templateValue.text))
    }

    fun insertToken(token: String) {
        val start = templateValue.selection.start.coerceIn(0, templateValue.text.length)
        val end = templateValue.selection.end.coerceIn(0, templateValue.text.length)
        val newText = buildString {
            append(templateValue.text.substring(0, start))
            append(token)
            append(templateValue.text.substring(end))
        }
        val cursor = start + token.length
        templateValue = templateValue.copy(text = newText, selection = TextRange(cursor))
        if (!templateFocused) {
            previewText = renderPreview(templateValue.text)
        }
    }

    fun normalizeTokenDeletion(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
        val oldText = oldValue.text
        val newText = newValue.text
        val oldSelection = oldValue.selection
        val newSelection = newValue.selection
        if (oldSelection.start != oldSelection.end) return newValue
        if (newText.length != oldText.length - 1) return newValue

        val oldCursor = oldSelection.start
        val isBackspace = newSelection.start == (oldCursor - 1).coerceAtLeast(0)
        val removeIndex = if (isBackspace) oldCursor - 1 else oldCursor
        if (removeIndex !in oldText.indices) return newValue

        val token = templateTokenRegex.findAll(oldText).firstOrNull { match ->
            removeIndex in match.range
        } ?: return newValue

        val start = token.range.first
        val endExclusive = token.range.last + 1
        val merged = oldText.removeRange(start, endExclusive)
        return TextFieldValue(
            text = merged,
            selection = TextRange(start.coerceAtMost(merged.length)),
        )
    }

    @Suppress("MagicNumber")
    LaunchedEffect(isFillTemplatePressed) {
        if (isFillTemplatePressed) {
            delay(10_000)
            if (isFillTemplatePressed) {
                suppressNextClick = true
                val fullTemplate = appNotifyFullTemplate()
                templateValue = TextFieldValue(
                    fullTemplate,
                    selection = TextRange(fullTemplate.length),
                )
            }
        }
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_FRACTION),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sender_app_config_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.sender_gate_ingress_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.sender_gate_ingress_summary_event),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_msg_type_app_notify_title),
                    summary = stringResource(id = R.string.pref_msg_type_app_notify_summary),
                    checked = appNotifyEnabled,
                    onCheckedChange = onAppNotifyToggle,
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.sender_gate_forwarding_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_forward_app_notify_title),
                    summary = stringResource(id = R.string.pref_forward_app_notify_summary),
                    checked = forwardAppNotifyEnabled,
                    onCheckedChange = onForwardAppNotifyToggle,
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = templateValue,
                    onValueChange = { newValue ->
                        templateValue = normalizeTokenDeletion(templateValue, newValue)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .onFocusChanged { focusState ->
                            if (templateFocused && !focusState.isFocused) {
                                previewText = renderPreview(templateValue.text)
                            }
                            templateFocused = focusState.isFocused
                        },
                    label = { Text(stringResource(R.string.sender_template_app_label)) },
                    placeholder = { Text(stringResource(R.string.sender_template_placeholder)) },
                    supportingText = { Text(stringResource(R.string.sender_template_supporting)) },
                )
                Text(
                    text = stringResource(R.string.sender_template_preview, previewText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            if (suppressNextClick) {
                                suppressNextClick = false
                                return@TextButton
                            }
                            val defaultTemplate = appNotifyDefaultTemplate()
                            templateValue = TextFieldValue(
                                defaultTemplate,
                                selection = TextRange(defaultTemplate.length),
                            )
                            previewText = renderPreview(templateValue.text)
                        },
                        interactionSource = fillTemplateInteractionSource,
                    ) {
                        Text(stringResource(R.string.sender_template_fill_default))
                    }
                }
                HorizontalDivider()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(appNotifyTemplateVariables.size) { index ->
                        val variable = appNotifyTemplateVariables[index]
                        OutlinedButton(
                            onClick = { insertToken(variable.token) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(stringResource(variable.labelRes), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(templateValue.text) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun CallNotifyTemplateDialog(
    currentTemplate: String,
    currentCommonConfig: ForwardCommonConfig,
    simRemarkSettings: SimRemarkSettingsSnapshot,
    callNotifyEnabled: Boolean,
    onCallNotifyToggle: (Boolean) -> Unit,
    forwardCallNotifyEnabled: Boolean,
    onForwardCallNotifyToggle: (Boolean) -> Unit,
    forwardCallNotifyFinalEnabled: Boolean,
    onForwardCallNotifyFinalToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    var templateValue by remember(currentTemplate) { mutableStateOf(TextFieldValue(currentTemplate)) }
    var templateFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val fillTemplateInteractionSource = remember { MutableInteractionSource() }
    val isFillTemplatePressed by fillTemplateInteractionSource.collectIsPressedAsState()
    var suppressNextClick by remember { mutableStateOf(false) }
    fun renderPreview(templateText: String): String {
        val previewConfig = currentCommonConfig.copy(messageTemplate = templateText)
        return ForwardCommonConfigStore.applyToMessage(
            context = context,
            messageType = MessageType.CALL_NOTIFY,
            msgInfo = buildCallNotifyPreviewMessage(context),
            config = previewConfig,
            simRemarkSnapshot = simRemarkSettings,
        ).content
    }
    var previewText by remember(currentTemplate, currentCommonConfig.deviceName) {
        mutableStateOf(renderPreview(templateValue.text))
    }

    fun insertToken(token: String) {
        val start = templateValue.selection.start.coerceIn(0, templateValue.text.length)
        val end = templateValue.selection.end.coerceIn(0, templateValue.text.length)
        val newText = buildString {
            append(templateValue.text.substring(0, start))
            append(token)
            append(templateValue.text.substring(end))
        }
        val cursor = start + token.length
        templateValue = templateValue.copy(text = newText, selection = TextRange(cursor))
        if (!templateFocused) {
            previewText = renderPreview(templateValue.text)
        }
    }

    fun normalizeTokenDeletion(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
        val oldText = oldValue.text
        val newText = newValue.text
        val oldSelection = oldValue.selection
        val newSelection = newValue.selection
        if (oldSelection.start != oldSelection.end) return newValue
        if (newText.length != oldText.length - 1) return newValue

        val oldCursor = oldSelection.start
        val isBackspace = newSelection.start == (oldCursor - 1).coerceAtLeast(0)
        val removeIndex = if (isBackspace) oldCursor - 1 else oldCursor
        if (removeIndex !in oldText.indices) return newValue

        val token = templateTokenRegex.findAll(oldText).firstOrNull { match ->
            removeIndex in match.range
        } ?: return newValue

        val start = token.range.first
        val endExclusive = token.range.last + 1
        val merged = oldText.removeRange(start, endExclusive)
        return TextFieldValue(
            text = merged,
            selection = TextRange(start.coerceAtMost(merged.length)),
        )
    }

    @Suppress("MagicNumber")
    LaunchedEffect(isFillTemplatePressed) {
        if (isFillTemplatePressed) {
            delay(10_000)
            if (isFillTemplatePressed) {
                suppressNextClick = true
                val fullTemplate = callNotifyFullTemplate()
                templateValue = TextFieldValue(
                    fullTemplate,
                    selection = TextRange(fullTemplate.length),
                )
            }
        }
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_FRACTION),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sender_call_config_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.sender_gate_ingress_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.sender_gate_ingress_summary_event),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_msg_type_call_notify_title),
                    summary = stringResource(id = R.string.pref_msg_type_call_notify_summary),
                    checked = callNotifyEnabled,
                    onCheckedChange = onCallNotifyToggle,
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.sender_gate_forwarding_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_forward_call_notify_title),
                    summary = stringResource(id = R.string.pref_forward_call_notify_summary),
                    checked = forwardCallNotifyEnabled,
                    onCheckedChange = onForwardCallNotifyToggle,
                )
                ConfigGateToggle(
                    title = stringResource(id = R.string.pref_forward_call_notify_final_title),
                    summary = stringResource(id = R.string.pref_forward_call_notify_final_summary),
                    checked = forwardCallNotifyFinalEnabled,
                    onCheckedChange = onForwardCallNotifyFinalToggle,
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = templateValue,
                    onValueChange = { newValue ->
                        templateValue = normalizeTokenDeletion(templateValue, newValue)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .onFocusChanged { focusState ->
                            if (templateFocused && !focusState.isFocused) {
                                previewText = renderPreview(templateValue.text)
                            }
                            templateFocused = focusState.isFocused
                        },
                    label = { Text(stringResource(R.string.sender_template_call_label)) },
                    placeholder = { Text(stringResource(R.string.sender_template_placeholder)) },
                    supportingText = { Text(stringResource(R.string.sender_template_supporting)) },
                )
                Text(
                    text = stringResource(R.string.sender_template_preview, previewText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            if (suppressNextClick) {
                                suppressNextClick = false
                                return@TextButton
                            }
                            val defaultTemplate = callNotifyDefaultTemplate()
                            templateValue = TextFieldValue(
                                defaultTemplate,
                                selection = TextRange(defaultTemplate.length),
                            )
                            previewText = renderPreview(templateValue.text)
                        },
                        interactionSource = fillTemplateInteractionSource,
                    ) {
                        Text(stringResource(R.string.sender_template_fill_default))
                    }
                }
                HorizontalDivider()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(callNotifyTemplateVariables.size) { index ->
                        val variable = callNotifyTemplateVariables[index]
                        OutlinedButton(
                            onClick = { insertToken(variable.token) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(stringResource(variable.labelRes), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(templateValue.text) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun SenderCard(
    sender: Sender,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sender.name.ifEmpty { getSenderTypeName(context, sender.type) },
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = sender.status == 1,
                    onCheckedChange = { onToggle(it) }
                )
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            Text(
                text = stringResource(
                    R.string.sender_type_line,
                    getSenderTypeName(context, sender.type),
                    sdf.format(sender.time),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

fun getSenderTypeName(context: android.content.Context, type: Int): String {
    return when (type) {
        SenderType.DINGTALK_GROUP_ROBOT -> context.getString(R.string.sender_type_dingtalk_group_robot)
        SenderType.EMAIL -> context.getString(R.string.sender_type_email)
        SenderType.BARK -> context.getString(R.string.sender_type_bark)
        SenderType.WEBHOOK -> context.getString(R.string.sender_type_webhook)
        SenderType.WEWORK_ROBOT -> context.getString(R.string.sender_type_wework_robot)
        SenderType.WEWORK_AGENT -> context.getString(R.string.sender_type_wework_agent)
        SenderType.SERVERCHAN -> context.getString(R.string.sender_type_serverchan)
        SenderType.TELEGRAM -> context.getString(R.string.sender_type_telegram)
        SenderType.SMS -> if (BuildConfig.ENABLE_SMS_CHANNEL) {
            context.getString(R.string.sender_type_sms)
        } else {
            context.getString(R.string.sender_type_sms_unavailable)
        }
        SenderType.FEISHU -> context.getString(R.string.sender_type_feishu)
        SenderType.PUSHPLUS -> context.getString(R.string.sender_type_pushplus)
        SenderType.GOTIFY -> context.getString(R.string.sender_type_gotify)
        SenderType.NTFY -> context.getString(R.string.sender_type_ntfy)
        SenderType.DINGTALK_INNER_ROBOT -> context.getString(R.string.sender_type_dingtalk_inner_robot)
        SenderType.FEISHU_APP -> context.getString(R.string.sender_type_feishu_app)
        SenderType.URL_SCHEME -> context.getString(R.string.sender_type_url_scheme)
        SenderType.SOCKET -> context.getString(R.string.sender_type_socket)
        else -> context.getString(R.string.sender_type_unknown)
    }
}
