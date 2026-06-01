@file:Suppress("LocalContextGetResourceValueCall", "NonObservableLocale")

package io.github.magisk317.relay.ui.sender

import io.github.magisk317.relay.ui.common.showLatestSnackbar

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.mobileui.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.contract.settings.ForwardTypeGateSnapshot
import io.github.magisk317.relay.contract.settings.ForwardTypeGateUpdate
import io.github.magisk317.relay.contract.settings.MessageTypeGateSnapshot
import io.github.magisk317.relay.contract.settings.MessageTypeGateUpdate
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.engine.model.Sender
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject

private const val DRAG_EDGE_SCROLL_THRESHOLD_PX = 96
private const val DRAG_EDGE_SCROLL_STEP_PX = 36f

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
    val listState = rememberLazyListState()
    val settingsRepository: SettingsPreferencesRepository = koinInject()
    val snackbarHostState = remember { SnackbarHostState() }
    val senders by viewModel.senderList.collectAsStateWithLifecycle()
    var displayedSenders by remember { mutableStateOf<List<Sender>>(emptyList()) }
    var draggingSenderId by remember { mutableStateOf<Long?>(null) }
    val latestDisplayedSenders by rememberUpdatedState(displayedSenders)
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
    var priorityEditingSender by remember { mutableStateOf<Sender?>(null) }
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
    LaunchedEffect(senders) {
        if (draggingSenderId == null) {
            displayedSenders = senders
        }
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
        SenderTypeDialog(
            onDismiss = { showTypeDialog = false },
            onAddClick = { type ->
                showTypeDialog = false
                onAddClick(type)
            },
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
                scope.launch { snackbarHostState.showLatestSnackbar(context.getString(R.string.pref_sync_snackbar)) }
            },
        )
    }
    if (showGeneralConfigDialog) {
        GeneralConfigDialog(
            currentConfig = commonConfig,
            currentSimSlot1Remark = simSlot1Remark,
            currentSimSlot2Remark = simSlot2Remark,
            onDismiss = { showGeneralConfigDialog = false },
            onSave = { config, sim1Remark, sim2Remark ->
                viewModel.saveForwardCommonConfig(config)
                viewModel.saveSimRemarkSettings(sim1Remark, sim2Remark)
                showGeneralConfigDialog = false
                scope.launch { snackbarHostState.showLatestSnackbar(context.getString(R.string.pref_sync_snackbar)) }
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
                scope.launch { snackbarHostState.showLatestSnackbar(context.getString(R.string.pref_sync_snackbar)) }
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
                scope.launch { snackbarHostState.showLatestSnackbar(context.getString(R.string.pref_sync_snackbar)) }
            },
        )
    }
    priorityEditingSender?.let { sender ->
        SenderPriorityDialog(
            sender = sender,
            currentPriority = displayedSenders.indexOfFirst { it.id == sender.id }.coerceAtLeast(0),
            maxPriority = displayedSenders.lastIndex.coerceAtLeast(0),
            onDismiss = { priorityEditingSender = null },
            onSave = { priority ->
                val reordered = reorderSenderToPriority(displayedSenders, sender.id, priority)
                displayedSenders = reordered
                viewModel.updateSenderPriorities(priorityMapForOrder(reordered))
                priorityEditingSender = null
            },
        )
    }

    fun moveDraggedSender(senderId: Long, dragOffset: Float): Boolean {
        val visibleSenderItems = listState.layoutInfo.visibleItemsInfo.filter { it.key is Long }
        val draggedInfo = visibleSenderItems.firstOrNull { it.key == senderId } ?: return false
        val draggedCenter = draggedInfo.offset + draggedInfo.size / 2f + dragOffset
        val targetId = visibleSenderItems.firstOrNull { item ->
            item.key != senderId && draggedCenter >= item.offset && draggedCenter <= item.offset + item.size
        }?.key as? Long ?: return false
        val current = latestDisplayedSenders
        val fromIndex = current.indexOfFirst { it.id == senderId }
        val toIndex = current.indexOfFirst { it.id == targetId }
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return false
        displayedSenders = current.moveItem(fromIndex, toIndex)
        return true
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
                state = listState,
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

                if (displayedSenders.isEmpty()) {
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
                    items(displayedSenders, key = { it.id }) { sender ->
                        var dragOffset by remember(sender.id) { mutableFloatStateOf(0f) }
                        val senderDragModifier = Modifier.pointerInput(sender.id) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    draggingSenderId = sender.id
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingSenderId = null
                                    displayedSenders = senders
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggingSenderId = null
                                    dragOffset = 0f
                                    viewModel.updateSenderPriorities(priorityMapForOrder(latestDisplayedSenders))
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount
                                    val viewportEnd = listState.layoutInfo.viewportEndOffset
                                    when {
                                        change.position.y < DRAG_EDGE_SCROLL_THRESHOLD_PX -> {
                                            scope.launch { listState.scrollBy(-DRAG_EDGE_SCROLL_STEP_PX) }
                                        }
                                        change.position.y > viewportEnd - DRAG_EDGE_SCROLL_THRESHOLD_PX -> {
                                            scope.launch { listState.scrollBy(DRAG_EDGE_SCROLL_STEP_PX) }
                                        }
                                    }
                                    if (moveDraggedSender(sender.id, dragOffset)) {
                                        dragOffset = 0f
                                    }
                                },
                            )
                        }
                        SenderCard(
                            sender = sender,
                            displayPriority = displayedSenders.indexOfFirst { it.id == sender.id }.coerceAtLeast(0),
                            dragModifier = senderDragModifier,
                            onEdit = { onEditClick(sender.id) },
                            onPriorityClick = { priorityEditingSender = sender },
                            onToggle = { enabled ->
                                if (enabled) {
                                    val result = viewModel.validateSenderForEnable(sender)
                                    if (!result.valid) {
                                        scope.launch {
                                            snackbarHostState.showLatestSnackbar(
                                                context.getString(R.string.sender_enable_failed, result.message),
                                            )
                                        }
                                    } else {
                                        viewModel.toggleSenderStatus(sender, enabled)
                                        scope.launch {
                                            snackbarHostState.showLatestSnackbar(context.getString(R.string.pref_sync_snackbar))
                                        }
                                    }
                                } else {
                                    viewModel.toggleSenderStatus(sender, enabled)
                                    scope.launch {
                                        snackbarHostState.showLatestSnackbar(context.getString(R.string.pref_sync_snackbar))
                                    }
                                }
                            },
                            onDelete = {
                                val removedSender = sender.copy()
                                viewModel.deleteSender(removedSender)
                                scope.launch {
                                    val resultDeferred = async {
                                        snackbarHostState.showLatestSnackbar(
                                            message = context.getString(
                                                R.string.sender_removed_with_undo,
                                                removedSender.name.ifBlank { getSenderTypeName(context, removedSender.type) },
                                            ),
                                            actionLabel = context.getString(R.string.revoke),
                                            duration = SnackbarDuration.Indefinite,
                                        )
                                    }
                                    delay(SENDER_UNDO_SNACKBAR_DURATION_MS)
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
                        totalDurationMs = SENDER_UNDO_SNACKBAR_DURATION_MS,
                    )
                },
            )
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
        SenderType.YUNHU -> context.getString(R.string.sender_type_yunhu)
        SenderType.FEISHU_BOT_TOKEN -> context.getString(R.string.sender_type_feishu_bot_token)
        else -> context.getString(R.string.sender_type_unknown)
    }
}
