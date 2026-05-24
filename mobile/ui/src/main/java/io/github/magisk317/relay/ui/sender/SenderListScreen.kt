@file:Suppress("LocalContextGetResourceValueCall", "NonObservableLocale")

package io.github.magisk317.relay.ui.sender

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.contract.constant.DispatchStrategy
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.mobileui.BuildConfig
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.contract.settings.ForwardTypeGateSnapshot
import io.github.magisk317.relay.contract.settings.ForwardTypeGateUpdate
import io.github.magisk317.relay.contract.settings.MessageTypeGateSnapshot
import io.github.magisk317.relay.contract.settings.MessageTypeGateUpdate
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.settings.SimRemarkSettingsSnapshot
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.contract.model.ForwardCommonConfig
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.ui.common.filterNonNegativeIntegerInput
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject

private const val DIALOG_WIDTH_FRACTION = 0.92f
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
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.pref_sync_snackbar)) }
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
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.pref_sync_snackbar)) }
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
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.pref_sync_snackbar)) }
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
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.pref_sync_snackbar)) }
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

@Composable
private fun GeneralConfigDialog(
    currentConfig: ForwardCommonConfig,
    currentSimSlot1Remark: String,
    currentSimSlot2Remark: String,
    onDismiss: () -> Unit,
    onSave: (ForwardCommonConfig, String, String) -> Unit,
) {
    var deviceName by remember(currentConfig.deviceName) { mutableStateOf(currentConfig.deviceName) }
    var dispatchStrategy by remember(currentConfig.dispatchStrategy) {
        mutableIntStateOf(normalizeDispatchStrategy(currentConfig.dispatchStrategy))
    }
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
                Text(
                    text = stringResource(R.string.dispatch_strategy),
                    style = MaterialTheme.typography.titleSmall,
                )
                SingleChoiceSegmentedSelector(
                    options = listOf(
                        SegmentedOption(
                            DispatchStrategy.PRIMARY_ONLY,
                            stringResource(R.string.dispatch_strategy_primary_only),
                        ),
                        SegmentedOption(
                            DispatchStrategy.BROADCAST_ALL,
                            stringResource(R.string.dispatch_strategy_broadcast_all),
                        ),
                        SegmentedOption(
                            DispatchStrategy.FAILOVER,
                            stringResource(R.string.dispatch_strategy_failover),
                        ),
                    ),
                    selected = dispatchStrategy,
                    onSelect = { dispatchStrategy = it },
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
                        currentConfig.copy(
                            deviceName = deviceName.trim(),
                            dispatchStrategy = dispatchStrategy,
                        ),
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
private fun SenderPriorityDialog(
    sender: Sender,
    currentPriority: Int,
    maxPriority: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val context = LocalContext.current
    var priorityText by remember(sender.id, currentPriority) { mutableStateOf(currentPriority.toString()) }
    val parsedPriority = priorityText.toIntOrNull()
    val validPriority = parsedPriority != null && parsedPriority >= 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    R.string.sender_priority_dialog_title,
                    sender.name.ifBlank { getSenderTypeName(context, sender.type) },
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = priorityText,
                onValueChange = { input ->
                    priorityText = filterNonNegativeIntegerInput(input).take(3)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.sender_priority_order)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = priorityText.isNotBlank() && !validPriority,
            )
        },
        confirmButton = {
            TextButton(
                enabled = validPriority,
                onClick = {
                    onSave((parsedPriority ?: currentPriority).coerceIn(0, maxPriority))
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
