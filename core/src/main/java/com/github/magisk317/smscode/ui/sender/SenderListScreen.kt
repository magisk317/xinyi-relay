package com.github.magisk317.smscode.ui.sender

import android.os.SystemClock
import android.widget.Toast
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.magisk317.smscode.forwarder.entity.ForwardCommonConfig
import com.github.magisk317.smscode.forwarder.entity.Sender
import com.github.magisk317.smscode.forwarder.utils.ForwardCommonConfigStore
import com.github.magisk317.smscode.forwarder.utils.SenderType
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import io.github.magisk317.xinyi.relay.core.BuildConfig
import io.github.magisk317.xinyi.relay.core.R
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

private data class TemplateVariable(
    val label: String,
    val token: String,
)

private val forwardTemplateVariables = listOf(
    TemplateVariable("来源号码", "{{FROM}}"),
    TemplateVariable("短信内容", "{{SMS}}"),
    TemplateVariable("通话类型", "{{CALL_TYPE}}"),
    TemplateVariable("卡槽备注", "{{CARD_SLOT}}"),
    TemplateVariable("卡槽主键", "{{CARD_SUBID}}"),
    TemplateVariable("来源姓名", "{{CONTACT_NAME}}"),
    TemplateVariable("来源归属", "{{PHONE_AREA}}"),
    TemplateVariable("APP包名", "{{PACKAGE_NAME}}"),
    TemplateVariable("APP应用名", "{{APP_NAME}}"),
    TemplateVariable("通知内容", "{{MSG}}"),
    TemplateVariable("电池电量", "{{BATTERY_PCT}}"),
    TemplateVariable("电池状态", "{{BATTERY_STATUS}}"),
    TemplateVariable("充电方式", "{{BATTERY_PLUGGED}}"),
    TemplateVariable("电池完整信息", "{{BATTERY_INFO}}"),
    TemplateVariable("电池简单信息", "{{BATTERY_INFO_SIMPLE}}"),
    TemplateVariable("公网IPv4", "{{IPV4}}"),
    TemplateVariable("公网IPv6", "{{IPV6}}"),
    TemplateVariable("IP地址列表", "{{IP_LIST}}"),
    TemplateVariable("网络状态", "{{NET_TYPE}}"),
    TemplateVariable("接收时间", "{{RECEIVE_TIME}}"),
    TemplateVariable("当前时间", "{{CURRENT_TIME}}"),
    TemplateVariable("设备名称", "{{DEVICE_NAME}}"),
    TemplateVariable("软件版本", "{{APP_VERSION}}"),
)
private val templateTokenRegex = Regex("\\{\\{[^{}]+\\}\\}")
private val cardSlotLineRegex = Regex("(?m)^(\\s*)卡槽([:：])")

private fun toAppNotifyTemplate(template: String): String {
    return template
        .replace(cardSlotLineRegex, "$1应用$2")
        .replace("【卡槽与来源】", "【应用与来源】")
}

private fun toCallNotifyTemplate(template: String): String {
    return template
        .replace("{{SMS}}", "{{CALL_TYPE}} {{SMS}}")
        .replace(cardSlotLineRegex, "$1通话$2")
        .replace("【卡槽与来源】", "【通话与来源】")
}

private fun appNotifyDefaultTemplate(): String = toAppNotifyTemplate(ForwardCommonConfigStore.defaultTemplate())
private fun appNotifyFullTemplate(): String = toAppNotifyTemplate(ForwardCommonConfigStore.fullInfoTemplate())
private fun callNotifyDefaultTemplate(): String = toCallNotifyTemplate(ForwardCommonConfigStore.defaultTemplate())
private fun callNotifyFullTemplate(): String = toCallNotifyTemplate(ForwardCommonConfigStore.fullInfoTemplate())

private val appNotifyTemplateVariables = forwardTemplateVariables.map { variable ->
    when (variable.token) {
        "{{CARD_SLOT}}" -> variable.copy(label = "应用备注")
        "{{CARD_SUBID}}" -> variable.copy(label = "应用主键")
        else -> variable
    }
}

private val callNotifyTemplateVariables = forwardTemplateVariables.map { variable ->
    when (variable.token) {
        "{{SMS}}" -> variable.copy(label = "通话详情")
        "{{CARD_SLOT}}" -> variable.copy(label = "通话来源")
        "{{CARD_SUBID}}" -> variable.copy(label = "通话主键")
        else -> variable
    }
}
private const val DIALOG_WIDTH_FRACTION = 0.92f
private const val UNDO_SNACKBAR_DURATION_MS = 5_000L
private const val UNDO_COUNTDOWN_TICK_MS = 50L

private fun buildSmsPreviewMessage(): com.github.magisk317.smscode.forwarder.entity.MsgInfo {
    return com.github.magisk317.smscode.forwarder.entity.MsgInfo(
        type = "sms",
        from = "10690001234",
        content = "【测试银行】您的验证码为 123456，请勿泄露。",
        date = Date(),
        simInfo = "SIM1",
        simSlot = 0,
        subId = 1,
        contactName = "测试银行",
        phoneArea = "上海",
    )
}

private fun buildAppNotifyPreviewMessage(): com.github.magisk317.smscode.forwarder.entity.MsgInfo {
    return com.github.magisk317.smscode.forwarder.entity.MsgInfo(
        type = "app_notify",
        from = "微信支付",
        content = "收款到账 52.00 元",
        date = Date(),
        simInfo = "微信",
        packageName = "com.tencent.mm",
        appName = "微信",
        title = "微信支付",
        message = "张三向你转账 52.00 元",
        contactName = "微信支付",
    )
}

private fun buildCallNotifyPreviewMessage(): com.github.magisk317.smscode.forwarder.entity.MsgInfo {
    return com.github.magisk317.smscode.forwarder.entity.MsgInfo(
        type = "call_notify",
        from = "10086",
        content = "时长 00:32",
        date = Date(),
        simInfo = "SIM1",
        simSlot = 0,
        subId = 42,
        callType = 3,
        contactName = "中国移动",
        phoneArea = "上海",
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderListScreen(
    viewModel: SenderViewModel = viewModel(),
    onAddClick: (Int) -> Unit,
    onEditClick: (Long) -> Unit,
    forceShowTypeDialog: Boolean = false,
    onForceShowHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val senders by viewModel.senderList.collectAsStateWithLifecycle()
    val commonConfig by viewModel.forwardCommonConfig.collectAsStateWithLifecycle()
    val appNotifyTemplate by viewModel.appNotifyTemplate.collectAsStateWithLifecycle()
    val callNotifyTemplate by viewModel.callNotifyTemplate.collectAsStateWithLifecycle()
    var showTypeDialog by remember { mutableStateOf(false) }
    var showGeneralConfigDialog by remember { mutableStateOf(false) }
    var showCommonConfigDialog by remember { mutableStateOf(false) }
    var showAppNotifyConfigDialog by remember { mutableStateOf(false) }
    var showCallNotifyConfigDialog by remember { mutableStateOf(false) }
    var simSlot1Remark by remember { mutableStateOf("") }
    var simSlot2Remark by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        simSlot1Remark = AppPreferencesDataStore.getString(context, PrefConst.KEY_SIM_SLOT1_REMARK, "")
        simSlot2Remark = AppPreferencesDataStore.getString(context, PrefConst.KEY_SIM_SLOT2_REMARK, "")
    }
    LaunchedEffect(forceShowTypeDialog) {
        if (forceShowTypeDialog) {
            showTypeDialog = true
            onForceShowHandled()
        }
    }

    if (showTypeDialog) {
        val otherChannels = mutableListOf(
            SenderType.EMAIL to "邮件",
            SenderType.URL_SCHEME to "Url Scheme",
            SenderType.SOCKET to "Socket",
        )
        if (BuildConfig.ENABLE_SMS_CHANNEL) {
            otherChannels.add(1, SenderType.SMS to "短信")
        }
        val supportedTypeGroups = listOf(
            "企业协作" to listOf(
                SenderType.DINGTALK_GROUP_ROBOT to "钉钉群机器人",
                SenderType.DINGTALK_INNER_ROBOT to "钉钉内部机器人",
                SenderType.FEISHU to "飞书机器人",
                SenderType.FEISHU_APP to "飞书应用",
                SenderType.WEWORK_ROBOT to "企微群机器人",
                SenderType.WEWORK_AGENT to "企微应用",
            ),
            "消息推送" to listOf(
                SenderType.TELEGRAM to "Telegram",
                SenderType.WEBHOOK to "Webhook",
                SenderType.SERVERCHAN to "Server酱",
                SenderType.PUSHPLUS to "PushPlus",
                SenderType.GOTIFY to "Gotify",
                SenderType.BARK to "Bark",
            ),
            "其他" to listOf(
                *otherChannels.toTypedArray(),
            ),
        )

        @Suppress("MagicNumber")
        AlertDialog(
            modifier = Modifier.fillMaxWidth(0.88f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showTypeDialog = false },
            title = { Text("选择新建通道类型") },
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
                    Text("取消")
                }
            }
        )
    }

    if (showCommonConfigDialog) {
        ForwardCommonConfigDialog(
            currentConfig = commonConfig,
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
                scope.launch {
                    AppPreferencesDataStore.setString(context, PrefConst.KEY_SIM_SLOT1_REMARK, sim1Remark)
                    AppPreferencesDataStore.setString(context, PrefConst.KEY_SIM_SLOT2_REMARK, sim2Remark)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                    simSlot1Remark = sim1Remark
                    simSlot2Remark = sim2Remark
                }
                showGeneralConfigDialog = false
            },
        )
    }
    if (showAppNotifyConfigDialog) {
        AppNotifyTemplateDialog(
            currentTemplate = appNotifyTemplate,
            currentCommonConfig = commonConfig,
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
            onDismiss = { showCallNotifyConfigDialog = false },
            onSave = {
                viewModel.saveCallNotifyTemplate(it)
                showCallNotifyConfigDialog = false
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("通道配置") }) },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 56.dp),
                onClick = { showTypeDialog = true },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Sender")
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
                            Text("暂无发送通道，请点击右下角添加")
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
                                        Toast.makeText(context, "无法开启：${result.message}", Toast.LENGTH_LONG).show()
                                    } else {
                                        viewModel.toggleSenderStatus(sender, enabled)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.pref_sync_toast),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                } else {
                                    viewModel.toggleSenderStatus(sender, enabled)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.pref_sync_toast),
                                        Toast.LENGTH_SHORT,
                                    ).show()
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
                                                removedSender.name.ifBlank { getSenderTypeName(removedSender.type) },
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
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            ) { data ->
                UndoCountdownSnackbar(
                    data = data,
                    totalDurationMs = UNDO_SNACKBAR_DURATION_MS,
                )
            }
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

    Snackbar(
        action = {
            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = { data.performAction() }) {
                    Text(label)
                }
            }
        },
        dismissAction = {
            CountdownCircle(
                progress = progress,
                seconds = remainingSeconds,
            )
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
                text = "通用配置",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                text = buildString {
                    append("设备: ")
                    append(deviceName.ifBlank { "默认系统值" })
                    append(" / SIM1: ")
                    append(simSlot1Remark.ifBlank { "未设置" })
                    append(" / SIM2: ")
                    append(simSlot2Remark.ifBlank { "未设置" })
                },
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
                text = "短信配置",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                text = "点击设置转发模板",
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
                text = "应用配置",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                text = "点击设置转发模板",
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
                text = "通话配置",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                text = "点击设置转发模板",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        title = { Text("通用配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("设备名称") },
                    placeholder = { Text("默认读取系统值") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = simSlot1Remark,
                    onValueChange = { simSlot1Remark = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("SIM1 备注") },
                    placeholder = { Text("用于 {{CARD_SLOT}} 显示") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = simSlot2Remark,
                    onValueChange = { simSlot2Remark = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("SIM2 备注") },
                    placeholder = { Text("用于 {{CARD_SLOT}} 显示") },
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
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ForwardCommonConfigDialog(
    currentConfig: ForwardCommonConfig,
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
            msgInfo = buildSmsPreviewMessage(),
            config = previewConfig,
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
        title = { Text("短信配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    label = { Text("转发信息模板") },
                    placeholder = { Text("留空使用默认模板") },
                    supportingText = { Text("Tip: 按需插入内容标签；可用变量见下方按钮") },
                )
                Text(
                    text = "效果预览：\n$previewText",
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
                        Text("填入默认模板")
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
                            Text(variable.label, style = MaterialTheme.typography.labelSmall)
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
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun AppNotifyTemplateDialog(
    currentTemplate: String,
    currentCommonConfig: ForwardCommonConfig,
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
            msgInfo = buildAppNotifyPreviewMessage(),
            config = previewConfig,
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
        title = { Text("应用通知配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    label = { Text("应用通知转发模板") },
                    placeholder = { Text("留空使用默认模板") },
                    supportingText = { Text("Tip: 按需插入内容标签；可用变量见下方按钮") },
                )
                Text(
                    text = "效果预览：\n$previewText",
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
                        Text("填入默认模板")
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
                            Text(variable.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(templateValue.text) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun CallNotifyTemplateDialog(
    currentTemplate: String,
    currentCommonConfig: ForwardCommonConfig,
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
            msgInfo = buildCallNotifyPreviewMessage(),
            config = previewConfig,
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
        title = { Text("通话通知配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    label = { Text("通话通知转发模板") },
                    placeholder = { Text("留空使用默认模板") },
                    supportingText = { Text("Tip: 按需插入内容标签；可用变量见下方按钮") },
                )
                Text(
                    text = "效果预览：\n$previewText",
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
                        Text("填入默认模板")
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
                            Text(variable.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(templateValue.text) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
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
                    text = sender.name.ifEmpty { getSenderTypeName(sender.type) },
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = sender.status == 1,
                    onCheckedChange = { onToggle(it) }
                )
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            Text(
                text = "类型: ${getSenderTypeName(sender.type)} | 修改于: ${sdf.format(sender.time)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

fun getSenderTypeName(type: Int): String {
    return when (type) {
        SenderType.DINGTALK_GROUP_ROBOT -> "钉钉群机器人"
        SenderType.EMAIL -> "邮件"
        SenderType.BARK -> "Bark"
        SenderType.WEBHOOK -> "Webhook"
        SenderType.WEWORK_ROBOT -> "企微群机器人"
        SenderType.WEWORK_AGENT -> "企微应用"
        SenderType.SERVERCHAN -> "Server酱"
        SenderType.TELEGRAM -> "Telegram机器人"
        SenderType.SMS -> if (BuildConfig.ENABLE_SMS_CHANNEL) "短信" else "短信(不可用)"
        SenderType.FEISHU -> "飞书机器人"
        SenderType.PUSHPLUS -> "PushPlus"
        SenderType.GOTIFY -> "Gotify"
        SenderType.DINGTALK_INNER_ROBOT -> "钉钉内部机器人"
        SenderType.FEISHU_APP -> "飞书应用"
        SenderType.URL_SCHEME -> "Url Scheme"
        SenderType.SOCKET -> "Socket"
        else -> "未知通道 ($type)"
    }
}
