package io.github.magisk317.relay.ui.record

import android.graphics.Color as AndroidColor
import android.content.ClipData
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.common.utils.AppPreferencesDataStore
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.relay.ui.common.AppIconImage
import io.github.magisk317.relay.ui.common.LoadingIndicatorTokens
import io.github.magisk317.relay.ui.common.PolygonMorphLoadingIndicator
import io.github.magisk317.relay.ui.common.SessionLoadingRegistry
import io.github.magisk317.relay.ui.common.rememberMinDurationLoading
import io.github.magisk317.relay.ui.home.Item
import io.github.magisk317.relay.ui.home.RetentionDialog
import io.github.magisk317.relay.ui.home.SectionHeader
import io.github.magisk317.relay.ui.home.SwitchItem
import io.github.magisk317.relay.ui.home.TextInputDialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

private enum class RecordExportScope {
    CURRENT_TAB,
    ALL_TABS,
}

private val FORWARD_SUCCESS_COLOR = Color(AndroidColor.parseColor("#2E7D32"))
private val FORWARD_FAILED_COLOR = Color(AndroidColor.parseColor("#C62828"))
private val FORWARD_WARNING_COLOR = Color(AndroidColor.parseColor("#B26A00"))
private val RECORD_TAB_ITEM_HEIGHT = 60.dp

private fun recordEnableKey(tab: Int): String = when (tab) {
    0 -> PrefConst.KEY_ENABLE_CODE_RECORDS_CODE
    1 -> PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS
    2 -> PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY
    else -> PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY
}

private fun recordEnableTitleRes(tab: Int): Int = when (tab) {
    0 -> R.string.pref_enable_code_records_title
    1 -> R.string.pref_enable_plain_sms_records_title
    2 -> R.string.pref_enable_app_notify_records_title
    else -> R.string.pref_enable_call_notify_records_title
}

private fun recordHistoryLimitKey(tab: Int): String = when (tab) {
    0 -> PrefConst.KEY_HISTORY_LIMIT_CODE
    1 -> PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS
    2 -> PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY
    else -> PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY
}

private fun recordTabNameRes(tab: Int): Int = when (tab) {
    0 -> R.string.record_settings_target_code
    1 -> R.string.record_settings_target_plain
    2 -> R.string.record_settings_target_app_notify
    else -> R.string.record_settings_target_call_notify
}

private fun recordColumnTitleRes(tab: Int): Int = when (tab) {
    0 -> R.string.records_column_code_title
    1 -> R.string.records_column_plain_title
    2 -> R.string.records_column_app_notify_title
    else -> R.string.records_column_call_notify_title
}

private fun recordColumnShortTitleRes(tab: Int): Int = when (tab) {
    0 -> R.string.records_column_code_short_title
    1 -> R.string.records_column_plain_short_title
    2 -> R.string.records_column_app_notify_short_title
    else -> R.string.records_column_call_notify_short_title
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun CodeRecordScreen(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: CodeRecordViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val smsList = uiState.smsList
    val isLoading = uiState.isLoading
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("records") }
    var initialLoadingStarted by remember { mutableStateOf(false) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var manualRefreshStartedAt by remember { mutableLongStateOf(0L) }
    val showLoading = rememberMinDurationLoading(
        actualLoading = isLoading && shouldShowInitialLoading,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(isLoading, shouldShowInitialLoading, initialLoadingStarted) {
        if (!shouldShowInitialLoading) return@LaunchedEffect
        if (isLoading) {
            initialLoadingStarted = true
        } else if (initialLoadingStarted) {
            SessionLoadingRegistry.markShown("records")
        }
    }

    LaunchedEffect(isLoading, manualRefreshing) {
        if (manualRefreshing && !isLoading) {
            val elapsed = if (manualRefreshStartedAt > 0L) {
                SystemClock.elapsedRealtime() - manualRefreshStartedAt
            } else {
                LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS
            }
            val remaining = (LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) delay(remaining)
            manualRefreshing = false
            manualRefreshStartedAt = 0L
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            manualRefreshStartedAt = SystemClock.elapsedRealtime()
            manualRefreshing = true
            viewModel.refreshData()
        }
    }

    val clipboard = LocalClipboard.current

    fun copyWithFeedback(label: String, text: String, toastText: String, snackbarText: String) {
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText(label, text).toClipEntry())
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            snackbarHostState.showSnackbar(snackbarText)
        }
    }

    // Initial Load
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    // Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedRecordTab by rememberSaveable { mutableIntStateOf(0) } // 0: code, 1: plain, 2: app_notify, 3: call_notify
    var fixedTopHeightPx by remember { mutableIntStateOf(0) }
    var exportScope by remember { mutableStateOf(RecordExportScope.CURRENT_TAB) }
    var pendingExportScope by remember { mutableStateOf(RecordExportScope.CURRENT_TAB) }
    var pendingExportTab by remember { mutableIntStateOf(0) }

    var historyLimitCode by remember { mutableStateOf("0") }
    var historyLimitPlain by remember { mutableStateOf("0") }
    var historyLimitAppNotify by remember { mutableStateOf("0") }
    var historyLimitCallNotify by remember { mutableStateOf("20") }
    var previousRecordEnabled by remember { mutableStateOf(true) }
    var showHistoryLimitDialog by remember { mutableStateOf(false) }
    var showHistoryLimitInput by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        previousRecordEnabled = AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS, true)
        val previousLimit = AppPreferencesDataStore.getString(context, PrefConst.KEY_HISTORY_LIMIT, "0")
        historyLimitCode = AppPreferencesDataStore.getString(context, PrefConst.KEY_HISTORY_LIMIT_CODE, previousLimit)
        historyLimitPlain = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS,
            previousLimit,
        )
        historyLimitAppNotify = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY,
            previousLimit,
        )
        historyLimitCallNotify = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY,
            "20",
        )
    }

    // Detail Dialog State
    var detailSmsMsg by remember { mutableStateOf<SmsMsg?>(null) }

    // Logic to toggle selection mode
    fun toggleSelection(id: Long) {
        val newSelection = selectedIds.toMutableSet()
        if (newSelection.contains(id)) {
            newSelection.remove(id)
        } else {
            newSelection.add(id)
        }
        selectedIds = newSelection
        if (newSelection.isEmpty()) {
            isSelectionMode = false
        }
    }

    // Back Handler
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    // Move deleteAndUndo outside items block and remember it
    val deleteAndUndo = remember(viewModel, scope, context, snackbarHostState) {
        { target: SmsMsg ->
            viewModel.removeSmsMsg(listOf(target))
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.some_items_removed, 1),
                    actionLabel = context.getString(R.string.revoke),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.restoreSmsMsgList(listOf(target))
                }
            }
        }
    }

    fun deleteSelected() {
        val deleteList = smsList.filter { sms -> sms.id != null && selectedIds.contains(sms.id) }
        if (deleteList.isEmpty()) return

        viewModel.removeSmsMsg(deleteList)
        isSelectionMode = false
        selectedIds = emptySet()

        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.some_items_removed, deleteList.size),
                actionLabel = context.getString(R.string.revoke),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreSmsMsgList(deleteList)
            }
        }
    }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                viewModel.exportRecords(
                    context = context,
                    uri = uri,
                    currentTab = pendingExportTab,
                    exportAllTabs = pendingExportScope == RecordExportScope.ALL_TABS,
                )
            }
        }

    if (showSettingsSheet) {
        val currentTabName = stringResource(recordTabNameRes(selectedRecordTab))
        val currentHistoryLimit = when (selectedRecordTab) {
            0 -> historyLimitCode
            1 -> historyLimitPlain
            2 -> historyLimitAppNotify
            else -> historyLimitCallNotify
        }
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionHeader(
                    text = stringResource(
                        id = R.string.record_settings_title_with_target,
                        currentTabName,
                    ),
                )
                SwitchItem(
                    title = stringResource(id = recordEnableTitleRes(selectedRecordTab)),
                    summary = "",
                    key = recordEnableKey(selectedRecordTab),
                    defaultValue = previousRecordEnabled,
                )

                Item(
                    title = stringResource(
                        id = R.string.pref_history_limit_title_with_target,
                        currentTabName,
                    ),
                    summary = run {
                        val entries = stringArrayResource(id = R.array.history_limit_entry_list)
                        val values = stringArrayResource(id = R.array.history_limit_value_list)
                        val index = values.indexOf(currentHistoryLimit)
                        if (index >= 0) {
                            entries[index]
                        } else {
                            "$currentHistoryLimit ${stringResource(recordTabNameRes(selectedRecordTab))}"
                        }
                    },
                ) { showHistoryLimitDialog = true }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showHistoryLimitDialog) {
        val currentHistoryLimit = when (selectedRecordTab) {
            0 -> historyLimitCode
            1 -> historyLimitPlain
            2 -> historyLimitAppNotify
            else -> historyLimitCallNotify
        }
        RetentionDialog(
            selectedValue = currentHistoryLimit,
            onDismiss = { showHistoryLimitDialog = false },
            titleId = R.string.pref_history_limit_title,
            entriesId = R.array.history_limit_entry_list,
            valuesId = R.array.history_limit_value_list,
        ) { value ->
            if (value == "-1") {
                showHistoryLimitInput = true
            } else {
                when (selectedRecordTab) {
                    0 -> historyLimitCode = value
                    1 -> historyLimitPlain = value
                    2 -> historyLimitAppNotify = value
                    else -> historyLimitCallNotify = value
                }
                scope.launch {
                    AppPreferencesDataStore.setString(context, recordHistoryLimitKey(selectedRecordTab), value)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                }
            }
            showHistoryLimitDialog = false
        }
    }

    if (showHistoryLimitInput) {
        val currentHistoryLimit = when (selectedRecordTab) {
            0 -> historyLimitCode
            1 -> historyLimitPlain
            2 -> historyLimitAppNotify
            else -> historyLimitCallNotify
        }
        TextInputDialog(
            title = stringResource(id = R.string.history_limit_custom_entry),
            initialValue = if (currentHistoryLimit == "0" || currentHistoryLimit == "-1") {
                ""
            } else {
                currentHistoryLimit
            },
            onDismiss = { showHistoryLimitInput = false },
        ) { value ->
            if (value.all { it.isDigit() } && value.isNotEmpty()) {
                when (selectedRecordTab) {
                    0 -> historyLimitCode = value
                    1 -> historyLimitPlain = value
                    2 -> historyLimitAppNotify = value
                    else -> historyLimitCallNotify = value
                }
                scope.launch {
                    AppPreferencesDataStore.setString(context, recordHistoryLimitKey(selectedRecordTab), value)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                }
            }
            showHistoryLimitInput = false
        }
    }

    if (showExportDialog) {
        val currentTabName = stringResource(recordTabNameRes(selectedRecordTab))
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.record_export_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { exportScope = RecordExportScope.CURRENT_TAB },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = exportScope == RecordExportScope.CURRENT_TAB,
                            onClick = { exportScope = RecordExportScope.CURRENT_TAB },
                        )
                        Text(
                            text = stringResource(R.string.record_export_current_tab_option, currentTabName),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { exportScope = RecordExportScope.ALL_TABS },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = exportScope == RecordExportScope.ALL_TABS,
                            onClick = { exportScope = RecordExportScope.ALL_TABS },
                        )
                        Text(text = stringResource(R.string.record_export_all_tabs_option))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingExportScope = exportScope
                        pendingExportTab = selectedRecordTab
                        val suffix = if (exportScope == RecordExportScope.ALL_TABS) {
                            "all"
                        } else {
                            when (selectedRecordTab) {
                                0 -> "code"
                                1 -> "plain"
                                2 -> "app_notify"
                                else -> "call_notify"
                            }
                        }
                        val filename = "Records_${suffix}_${SimpleDateFormat(
                            "yyyyMMdd_HHmm",
                            Locale.getDefault(),
                        ).format(Date())}.json"
                        showExportDialog = false
                        exportLauncher.launch(filename)
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val density = LocalDensity.current

    val codeSmsList = smsList.filter { it.msgType == SmsMsg.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank() }
    val plainSmsList = smsList.filter { it.msgType == SmsMsg.MSG_TYPE_SMS && it.smsCode.isNullOrBlank() }
    val appNotifyList = smsList.filter { it.msgType == SmsMsg.MSG_TYPE_APP_NOTIFY }
    val callNotifyList = smsList.filter { it.msgType == SmsMsg.MSG_TYPE_CALL_NOTIFY }
    val activeSmsList = when (selectedRecordTab) {
        0 -> codeSmsList
        1 -> plainSmsList
        2 -> appNotifyList
        else -> callNotifyList
    }
    val activeTitle = context.getString(recordColumnTitleRes(selectedRecordTab))
    val activeEmptyHint = when (selectedRecordTab) {
        0 -> context.getString(R.string.records_column_code_empty)
        1 -> context.getString(R.string.records_column_plain_empty)
        2 -> context.getString(R.string.records_column_app_notify_empty)
        else -> context.getString(R.string.records_column_call_notify_empty)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val defaultTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 120.dp
        val fixedTopHeight = if (fixedTopHeightPx > 0) with(density) { fixedTopHeightPx.toDp() } else defaultTopPadding
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = manualRefreshing,
            onRefresh = {
                manualRefreshStartedAt = SystemClock.elapsedRealtime()
                manualRefreshing = true
                viewModel.refreshData()
            },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = fixedTopHeight + LoadingIndicatorTokens.OverlayTopSpacing),
                    isRefreshing = manualRefreshing,
                    state = pullToRefreshState,
                )
            },
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
            ) {
                AnimatedContent(
                    targetState = Pair(showLoading, smsList),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "CodeRecordState",
                ) { (loading, list) ->
                    if (loading && !manualRefreshing && list.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            PolygonMorphLoadingIndicator(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = fixedTopHeight + LoadingIndicatorTokens.OverlayTopSpacing),
                            )
                        }
                    } else if (list.isEmpty() && !loading) {
                        // Empty View
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.list_empty_prompt),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val codeSmsList = list.filter { it.msgType == SmsMsg.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank() }
                        val plainSmsList = list.filter { it.msgType == SmsMsg.MSG_TYPE_SMS && it.smsCode.isNullOrBlank() }
                        val appNotifyList = list.filter { it.msgType == SmsMsg.MSG_TYPE_APP_NOTIFY }
                        val callNotifyList = list.filter { it.msgType == SmsMsg.MSG_TYPE_CALL_NOTIFY }
                        val activeSmsList = when (selectedRecordTab) {
                            0 -> codeSmsList
                            1 -> plainSmsList
                            2 -> appNotifyList
                            else -> callNotifyList
                        }

                        RecordSplitColumn(
                            title = activeTitle,
                            emptyHint = activeEmptyHint,
                            list = activeSmsList,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onToggleSelection = { toggleSelection(it) },
                            onActivateSelection = {
                                isSelectionMode = true
                                toggleSelection(it)
                            },
                            onCopyCode = { smsMsg ->
                                val code = smsMsg.smsCode
                                if (!code.isNullOrEmpty()) {
                                    val message = context.getString(R.string.prompt_sms_code_copied, code)
                                    copyWithFeedback("sms_code", code, message, message)
                                }
                            },
                            onShowDetail = { detailSmsMsg = it },
                            onDelete = { deleteAndUndo(it) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            scrollBehavior = scrollBehavior,
                            showHeader = false,
                            listContentPadding = PaddingValues(top = fixedTopHeight, bottom = bottomPadding),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .onSizeChanged { fixedTopHeightPx = it.height }
                .hazeEffect(hazeState, hazeStyle) {
                    forceInvalidateOnPreDraw = true
                },
        ) {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(stringResource(R.string.selected_count, selectedIds.size))
                    } else {
                        Text(stringResource(R.string.tab_records))
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    } else if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            val visibleIds = when (selectedRecordTab) {
                                0 -> smsList.filter { it.msgType == SmsMsg.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank() }
                                1 -> smsList.filter { it.msgType == SmsMsg.MSG_TYPE_SMS && it.smsCode.isNullOrBlank() }
                                2 -> smsList.filter { it.msgType == SmsMsg.MSG_TYPE_APP_NOTIFY }
                                else -> smsList.filter { it.msgType == SmsMsg.MSG_TYPE_CALL_NOTIFY }
                            }.mapNotNull { it.id }.toSet()
                            if (visibleIds.isEmpty()) return@IconButton
                            val allVisibleSelected = visibleIds.all { selectedIds.contains(it) }
                            selectedIds = if (allVisibleSelected) {
                                selectedIds - visibleIds
                            } else {
                                selectedIds + visibleIds
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_select_all))
                        }
                        IconButton(onClick = { deleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    } else {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = stringResource(R.string.pref_code_records_title),
                            )
                        }
                        IconButton(onClick = {
                            exportScope = RecordExportScope.CURRENT_TAB
                            showExportDialog = true
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_export),
                                contentDescription = stringResource(R.string.action_export_rules),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
            )
            if (smsList.isNotEmpty()) {
                val tabCounts = listOf(
                    codeSmsList.size,
                    plainSmsList.size,
                    appNotifyList.size,
                    callNotifyList.size,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                ) {
                    repeat(tabCounts.size) { tabIndex ->
                        val selected = selectedRecordTab == tabIndex
                        val shortTitle = stringResource(recordColumnShortTitleRes(tabIndex))
                        val text = if (selected) {
                            "$shortTitle（${tabCounts[tabIndex]}）"
                        } else {
                            shortTitle
                        }
                        val icon = when (tabIndex) {
                            0 -> Icons.Default.VpnKey
                            1 -> Icons.Default.Sms
                            2 -> Icons.Default.Notifications
                            else -> Icons.Default.Call
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(RECORD_TAB_ITEM_HEIGHT)
                                .clickable { selectedRecordTab = tabIndex },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )

        val sms = detailSmsMsg
        if (sms != null) {
            RecordDetailOverlay(
                hazeState = hazeState,
                hazeStyle = hazeStyle,
                sms = sms,
                onDismiss = { detailSmsMsg = null },
                onCopy = { label, value, toast ->
                    copyWithFeedback(label, value, toast, toast)
                },
                onDelete = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.some_items_removed, 1),
                        Toast.LENGTH_SHORT,
                    ).show()
                    deleteAndUndo(sms)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordDetailOverlay(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    sms: SmsMsg,
    onDismiss: () -> Unit,
    onCopy: (label: String, value: String, toast: String) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val isAppNotification = sms.msgType == SmsMsg.MSG_TYPE_APP_NOTIFY
    val detailDateFormatter = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()) }
    val sender = sms.sender ?: sms.company ?: context.getString(R.string.unknown)
    val time = detailDateFormatter.format(Date(sms.date))
    val content = sms.body.orEmpty()
    val forwardStatusAnnotated = resolveForwardStatusAnnotated(sms)
    val forwardTarget = sanitizeForwardTarget(sms.forwardTarget)
    val forwardTime = if (sms.forwardTime > 0L) detailDateFormatter.format(Date(sms.forwardTime)) else "-"
    val forwardMessageAnnotated = resolveForwardMessageAnnotated(sms.forwardMessage)
    val dismissInteraction = remember { MutableInteractionSource() }
    val detailTitleRes = if (isAppNotification) R.string.message_details_notification else R.string.message_details
    val copyTextRes = if (isAppNotification) R.string.copy_notification else R.string.copy_sms
    val copyToastRes = if (isAppNotification) R.string.prompt_notification_copied else R.string.prompt_sms_copied
    val deleteTextRes =
        if (isAppNotification) R.string.delete_notification_action else R.string.delete_sms_action
    val copyLabel = if (isAppNotification) "app_notification_body" else "sms_body"
    val appDisplayName = remember(sms.packageName) {
        if (!isAppNotification) {
            null
        } else {
            val pkg = sms.packageName.orEmpty()
            if (pkg.isBlank()) {
                null
            } else {
                runCatching {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString().ifBlank { pkg }
                }.getOrDefault(pkg)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeEffect(hazeState, hazeStyle) { forceInvalidateOnPreDraw = true }
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
            .clickable(
                interactionSource = dismissInteraction,
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(detailTitleRes),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.detail_click_copy_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isAppNotification && !appDisplayName.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${stringResource(R.string.detail_app)}:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = appDisplayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                val message = context.getString(
                                    R.string.prompt_field_copied,
                                    context.getString(R.string.detail_app),
                                )
                                onCopy("app_name", appDisplayName, message)
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.detail_sender)}:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = sender,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val message = context.getString(
                                R.string.prompt_field_copied,
                                context.getString(R.string.detail_sender),
                            )
                            onCopy("sms_sender", sender, message)
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.detail_time)}:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val message = context.getString(
                                R.string.prompt_field_copied,
                                context.getString(R.string.detail_time),
                            )
                            onCopy("sms_time", time, message)
                        },
                    )
                }
                Text(
                    text = "${stringResource(R.string.detail_content)}:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        if (content.isNotEmpty()) {
                            val message = context.getString(
                                R.string.prompt_field_copied,
                                context.getString(R.string.detail_content),
                            )
                            onCopy("sms_body", content, message)
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.detail_forward_status)}:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = forwardStatusAnnotated,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.detail_forward_target)}:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = forwardTarget,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.detail_forward_time)}:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = forwardTime,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${stringResource(R.string.detail_forward_message)}:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = forwardMessageAnnotated,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HorizontalDivider()
                ButtonGroup(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    overflowIndicator = { menuState ->
                        ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                    },
                ) {
                    customItem(
                        buttonGroupContent = {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (content.isNotEmpty()) {
                                        val message = context.getString(copyToastRes)
                                        onCopy(copyLabel, content, message)
                                    }
                                    onDismiss()
                                },
                            ) {
                                Text(stringResource(copyTextRes))
                            }
                        },
                        menuContent = { menuState ->
                            DropdownMenuItem(
                                text = { Text(stringResource(copyTextRes)) },
                                onClick = {
                                    if (content.isNotEmpty()) {
                                        val message = context.getString(copyToastRes)
                                        onCopy(copyLabel, content, message)
                                    }
                                    menuState.dismiss()
                                    onDismiss()
                                },
                            )
                        },
                    )
                    customItem(
                        buttonGroupContent = {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onDelete()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            ) {
                                Text(stringResource(deleteTextRes))
                            }
                        },
                        menuContent = { menuState ->
                            DropdownMenuItem(
                                text = { Text(stringResource(deleteTextRes)) },
                                onClick = {
                                    onDelete()
                                    menuState.dismiss()
                                    onDismiss()
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun sanitizeForwardTarget(rawTarget: String?): String {
    if (rawTarget.isNullOrBlank()) return "-"
    val channels = rawTarget.split("|")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { segment ->
            val channel = segment.substringBefore(":", segment).trim()
            if (channel.isEmpty()) segment else channel
        }
        .distinct()
    return if (channels.isEmpty()) "-" else channels.joinToString(" | ")
}

private data class ForwardCounts(val success: Int, val failed: Int)

private fun parseForwardCountsFromMessage(rawMessage: String?): ForwardCounts {
    if (rawMessage.isNullOrBlank()) return ForwardCounts(success = 0, failed = 0)
    var success = 0
    var failed = 0
    rawMessage.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { line ->
            when {
                line.contains("转发成功") -> success++
                line.contains("转发失败") -> failed++
            }
        }
    return ForwardCounts(success = success, failed = failed)
}

private fun countForwardTargets(rawTarget: String?): Int {
    if (rawTarget.isNullOrBlank()) return 0
    return rawTarget.split(",", "|")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .size
}

private fun formatForwardMessage(rawMessage: String?): String {
    if (rawMessage.isNullOrBlank()) return "-"
    val lines = rawMessage
        .replace(Regex("\\s*\\|\\s*"), "\n")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
    if (lines.isEmpty()) return "-"
    return lines.mapIndexed { index, line -> "${index + 1}. $line" }.joinToString("\n")
}

@Composable
private fun resolveForwardMessageAnnotated(rawMessage: String?): AnnotatedString {
    if (rawMessage.isNullOrBlank()) return AnnotatedString("-")
    val lines = rawMessage
        .replace(Regex("\\s*\\|\\s*"), "\n")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
    if (lines.isEmpty()) return AnnotatedString("-")
    return buildAnnotatedString {
        lines.forEachIndexed { index, line ->
            if (index > 0) append("\n")
            val color = when {
                line.contains("转发成功") -> FORWARD_SUCCESS_COLOR
                line.contains("转发失败") -> FORWARD_FAILED_COLOR
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            pushStyle(SpanStyle(color = color))
            append("${index + 1}. $line")
            pop()
        }
    }
}

private data class ForwardStatusSnapshot(
    val status: Int,
    val successCount: Int,
    val failedCount: Int,
)

private fun resolveForwardStatusSnapshot(smsMsg: SmsMsg): ForwardStatusSnapshot {
    val parsed = parseForwardCountsFromMessage(smsMsg.forwardMessage)
    val targetCount = countForwardTargets(smsMsg.forwardTarget)
    val statusForDisplay = when {
        parsed.success > 0 && parsed.failed > 0 -> SmsMsg.FORWARD_STATUS_PARTIAL
        parsed.success > 0 -> SmsMsg.FORWARD_STATUS_SUCCESS
        parsed.failed > 0 -> SmsMsg.FORWARD_STATUS_FAILED
        else -> smsMsg.forwardStatus
    }
    val successCount = when {
        parsed.success > 0 -> parsed.success
        statusForDisplay == SmsMsg.FORWARD_STATUS_SUCCESS -> maxOf(targetCount, 1)
        else -> 0
    }
    val failedCount = when {
        parsed.failed > 0 -> parsed.failed
        statusForDisplay == SmsMsg.FORWARD_STATUS_FAILED -> maxOf(targetCount, 1)
        else -> 0
    }
    return ForwardStatusSnapshot(
        status = statusForDisplay,
        successCount = successCount,
        failedCount = failedCount,
    )
}

@Composable
private fun resolveForwardStatusText(smsMsg: SmsMsg): String {
    val snapshot = resolveForwardStatusSnapshot(smsMsg)
    return when (snapshot.status) {
        SmsMsg.FORWARD_STATUS_BLOCKED -> stringResource(R.string.forward_status_none)
        SmsMsg.FORWARD_STATUS_PARTIAL -> stringResource(
            R.string.forward_status_partial,
            maxOf(snapshot.successCount, 1),
            maxOf(snapshot.failedCount, 1),
        )
        SmsMsg.FORWARD_STATUS_SUCCESS -> stringResource(
            R.string.forward_status_success_count,
            maxOf(snapshot.successCount, 1),
        )
        SmsMsg.FORWARD_STATUS_FAILED -> stringResource(
            R.string.forward_status_failed_count,
            maxOf(snapshot.failedCount, 1),
        )
        else -> stringResource(R.string.forward_status_none)
    }
}

@Composable
private fun resolveForwardStatusColor(smsMsg: SmsMsg): Color {
    return when (resolveForwardStatusSnapshot(smsMsg).status) {
        SmsMsg.FORWARD_STATUS_SUCCESS -> FORWARD_SUCCESS_COLOR
        SmsMsg.FORWARD_STATUS_FAILED -> FORWARD_FAILED_COLOR
        SmsMsg.FORWARD_STATUS_PARTIAL,
        SmsMsg.FORWARD_STATUS_BLOCKED,
        SmsMsg.FORWARD_STATUS_NONE,
        -> FORWARD_WARNING_COLOR
        else -> FORWARD_WARNING_COLOR
    }
}

@Composable
private fun resolveForwardStatusAnnotated(smsMsg: SmsMsg): AnnotatedString {
    val snapshot = resolveForwardStatusSnapshot(smsMsg)
    return if (snapshot.status == SmsMsg.FORWARD_STATUS_PARTIAL) {
        val successText = stringResource(
            R.string.forward_status_success_count,
            maxOf(snapshot.successCount, 1),
        )
        val failedText = stringResource(
            R.string.forward_status_failed_count,
            maxOf(snapshot.failedCount, 1),
        )
        buildAnnotatedString {
            pushStyle(SpanStyle(color = FORWARD_SUCCESS_COLOR))
            append(successText)
            pop()
            append(" ")
            pushStyle(SpanStyle(color = FORWARD_FAILED_COLOR))
            append(failedText)
            pop()
        }
    } else {
        val text = resolveForwardStatusText(smsMsg)
        val color = resolveForwardStatusColor(smsMsg)
        buildAnnotatedString {
            pushStyle(SpanStyle(color = color))
            append(text)
            pop()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RecordSplitColumn(
    title: String,
    emptyHint: String,
    list: List<SmsMsg>,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onActivateSelection: (Long) -> Unit,
    onCopyCode: (SmsMsg) -> Unit,
    onShowDetail: (SmsMsg) -> Unit,
    onDelete: (SmsMsg) -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
    showHeader: Boolean = true,
    listContentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val listState = rememberLazyListState()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = list.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
            if (list.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    state = listState,
                    contentPadding = listContentPadding,
                ) {
                    items(list, key = { it.id ?: 0 }) { smsMsg ->
                        val isSelected = selectedIds.contains(smsMsg.id)
                        if (isSelectionMode) {
                            if (smsMsg.msgType == SmsMsg.MSG_TYPE_APP_NOTIFY) {
                                AppNotificationItem(
                                    smsMsg = smsMsg,
                                    isSelectionMode = true,
                                    isSelected = isSelected,
                                    onClick = { onToggleSelection(smsMsg.id ?: 0) },
                                    onLongClick = {},
                                    onDetailClick = { onShowDetail(smsMsg) },
                                    modifier = Modifier.animateItem(),
                                )
                            } else {
                                CodeRecordItem(
                                    smsMsg = smsMsg,
                                    isSelectionMode = true,
                                    isSelected = isSelected,
                                    onClick = { onToggleSelection(smsMsg.id ?: 0) },
                                    onLongClick = {},
                                    onDetailClick = { onShowDetail(smsMsg) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        } else {
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                    onDelete(smsMsg)
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = if (dismissState.dismissDirection ==
                                            SwipeToDismissBoxValue.StartToEnd
                                        ) {
                                            Alignment.CenterStart
                                        } else {
                                            Alignment.CenterEnd
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.remove),
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                },
                                content = {
                                    if (smsMsg.msgType == SmsMsg.MSG_TYPE_APP_NOTIFY) {
                                        AppNotificationItem(
                                            smsMsg = smsMsg,
                                            isSelectionMode = false,
                                            isSelected = false,
                                            onClick = { onCopyCode(smsMsg) },
                                            onLongClick = { onActivateSelection(smsMsg.id ?: 0) },
                                            onDetailClick = { onShowDetail(smsMsg) },
                                            modifier = Modifier.animateItem(),
                                        )
                                    } else {
                                        CodeRecordItem(
                                            smsMsg = smsMsg,
                                            isSelectionMode = false,
                                            isSelected = false,
                                            onClick = { onCopyCode(smsMsg) },
                                            onLongClick = { onActivateSelection(smsMsg.id ?: 0) },
                                            onDetailClick = { onShowDetail(smsMsg) },
                                            modifier = Modifier.animateItem(),
                                        )
                                    }
                                },
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeRecordItem(
    smsMsg: SmsMsg,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()) }
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        // Left Side: Icon + App Name
        val fallbackLabel = (smsMsg.company ?: smsMsg.sender ?: stringResource(R.string.unknown))
            .trim()
            .trim('【', '】', '[', ']')
        val appLabel = remember(smsMsg.packageName) {
            val pkg = smsMsg.packageName
            if (pkg.isNullOrBlank()) {
                null
            } else {
                runCatching {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrNull()
            }
        }
        val displayLabel = appLabel ?: fallbackLabel
        val iconLabel = if (smsMsg.packageName.isNullOrBlank()) {
            fallbackLabel.replace(Regex("[【】\\[\\]]"), "").trim()
        } else {
            null
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(92.dp)
                .padding(end = 16.dp),
        ) {
            AppIconImage(
                packageName = smsMsg.packageName,
                label = iconLabel,
                contentDescription = stringResource(R.string.sms_icon_description),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
            )
        }

        // Right Side
        Column(modifier = Modifier.weight(1f)) {
            val hasCode = !smsMsg.smsCode.isNullOrBlank()
            val codeOrSender = smsMsg.smsCode?.takeIf { it.isNotBlank() }
                ?: smsMsg.sender?.takeIf { it.isNotBlank() }
                ?: fallbackLabel
            // Top Row: Code + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = codeOrSender,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 1,
                    overflow = if (hasCode) TextOverflow.Ellipsis else TextOverflow.Clip,
                    modifier = if (hasCode) {
                        Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    } else {
                        // Keep a fixed 10-char-like viewport for phone number marquee.
                        Modifier
                            .width(120.dp)
                            .basicMarquee()
                    },
                )
                if (!hasCode) {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Text(
                    text = dateFormatter.format(Date(smsMsg.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val body = smsMsg.body
            if (!body.isNullOrEmpty()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onDetailClick() },
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            val forwardStatusAnnotated = resolveForwardStatusAnnotated(smsMsg)
            Text(
                text = buildAnnotatedString {
                    append("${stringResource(R.string.detail_forward_status)}: ")
                    append(forwardStatusAnnotated)
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppNotificationItem(
    smsMsg: SmsMsg,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 16.dp).align(Alignment.CenterVertically),
            )
        }

        val fallbackLabel = (smsMsg.company ?: smsMsg.sender ?: stringResource(R.string.unknown))
            .trim()
            .trim('【', '】', '[', ']')
        val appLabel = remember(smsMsg.packageName) {
            val pkg = smsMsg.packageName
            if (pkg.isNullOrBlank()) {
                null
            } else {
                runCatching {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrNull()
            }
        }
        val displayLabel = appLabel ?: fallbackLabel

        AppIconImage(
            packageName = smsMsg.packageName,
            label = null,
            contentDescription = stringResource(R.string.sms_icon_description),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Text(
                    text = dateFormatter.format(Date(smsMsg.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = smsMsg.sender ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val body = smsMsg.body
            if (!body.isNullOrEmpty()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onDetailClick() },
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val forwardStatusAnnotated = resolveForwardStatusAnnotated(smsMsg)
            Text(
                text = buildAnnotatedString {
                    append("${stringResource(R.string.detail_forward_status)}: ")
                    append(forwardStatusAnnotated)
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
