@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.relay.ui.record

import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.common.showLatestSnackbar
import android.content.ClipData
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.relay.mobile.core.R
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.forward.engine.model.ReadRecordData
import io.github.magisk317.relay.ui.common.rememberPrefBoolean
import io.github.magisk317.relay.android.otel.MagiskOtelBootstrap
import io.github.magisk317.uikit.foundation.LoadingIndicatorTokens
import io.github.magisk317.uikit.foundation.PolygonMorphLoadingIndicator
import io.github.magisk317.uikit.foundation.SessionLoadingRegistry
import io.github.magisk317.uikit.foundation.rememberMinDurationLoading
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppPullToRefresh
import io.github.magisk317.uikit.surface.WorkspaceEmptyState
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun CodeRecordScreen(
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: CodeRecordViewModel = koinViewModel(),
    scrollChromeState: ScrollChromeState? = null,
    isActive: Boolean = true,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    benchmarkTagsEnabled: Boolean = true,
) {
    DisposableEffect(viewModel, isActive) {
        viewModel.setPageActive(isActive)
        onDispose {
            if (isActive) viewModel.setPageActive(false)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val smsList = uiState.smsList
    val queryState = uiState.queryState
    val packageLabels = uiState.packageLabels
    val defaultSmsPackage = uiState.defaultSmsPackage
    val defaultDialerPackage = uiState.defaultDialerPackage
    val recordIcons = uiState.recordIcons
    val isLoading = uiState.isLoading
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("records") }
    var initialLoadingStarted by remember { mutableStateOf(false) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var manualRefreshStartedAt by remember { mutableLongStateOf(0L) }
    // Do not replay an old top-level reselect when a new scoped Records screen is created later.
    var handledRefreshTrigger by rememberSaveable { mutableIntStateOf(refreshTrigger) }
    val showLoading = rememberMinDurationLoading(
        actualLoading = isActive && isLoading && shouldShowInitialLoading,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )
    val snackbarHostState = remember { AppSnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val iconDensity = LocalDensity.current
    val targetRecordIconPx = remember(iconDensity) {
        with(iconDensity) { APP_NOTIFY_ICON_SIZE.roundToPx() }
    }
    val settingsRepository: SettingsPreferencesRepository = koinInject()
    val savedSnackbarText = stringResource(id = R.string.pref_sync_snackbar)

    LaunchedEffect(isActive, isLoading, shouldShowInitialLoading, initialLoadingStarted) {
        if (!isActive) return@LaunchedEffect
        if (!shouldShowInitialLoading) return@LaunchedEffect
        if (isLoading) {
            initialLoadingStarted = true
        } else if (initialLoadingStarted) {
            SessionLoadingRegistry.markShown("records")
        }
    }

    LaunchedEffect(isActive, isLoading, manualRefreshing) {
        if (!isActive) {
            manualRefreshing = false
            manualRefreshStartedAt = 0L
            return@LaunchedEffect
        }
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

    LaunchedEffect(isActive, refreshTrigger) {
        if (!isActive || refreshTrigger <= 0 || refreshTrigger == handledRefreshTrigger) {
            return@LaunchedEffect
        }
        handledRefreshTrigger = refreshTrigger
        manualRefreshStartedAt = SystemClock.elapsedRealtime()
        manualRefreshing = true
        viewModel.refreshData()
    }

    val clipboard = LocalClipboard.current

    fun copyWithFeedback(label: String, text: String, snackbarText: String) {
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText(label, text).toClipEntry())
            snackbarHostState.showLatestSnackbar(snackbarText)
        }
    }

    // Initial Load
    LaunchedEffect(isActive) {
        if (isActive) viewModel.loadData()
    }

    // Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChartSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedRecordTab by rememberSaveable { mutableIntStateOf(0) } // 0: code, 1: plain, 2: app_notify, 3: call_notify
    var exportScope by remember { mutableStateOf(RecordExportScope.CURRENT_TAB) }
    var pendingExportScope by remember { mutableStateOf(RecordExportScope.CURRENT_TAB) }
    var pendingExportTab by remember { mutableIntStateOf(0) }

    var historyLimitCode by remember { mutableStateOf("0") }
    var historyLimitPlain by remember { mutableStateOf("0") }
    var historyLimitAppNotify by remember { mutableStateOf("0") }
    var historyLimitCallNotify by remember { mutableStateOf("20") }
    var codeRecordEnabled by remember { mutableStateOf(true) }
    var plainRecordEnabled by remember { mutableStateOf(true) }
    var appNotifyRecordEnabled by remember { mutableStateOf(true) }
    var callNotifyRecordEnabled by remember { mutableStateOf(true) }
    var showHistoryLimitDialog by remember { mutableStateOf(false) }
    var showHistoryLimitInput by remember { mutableStateOf(false) }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        val settings = settingsRepository.getRecordSettings()
        codeRecordEnabled = settings.codeRecordEnabled
        plainRecordEnabled = settings.plainSmsRecordEnabled
        appNotifyRecordEnabled = settings.appNotifyRecordEnabled
        callNotifyRecordEnabled = settings.callNotifyRecordEnabled
        historyLimitCode = settings.codeHistoryLimit
        historyLimitPlain = settings.plainSmsHistoryLimit
        historyLimitAppNotify = settings.appNotifyHistoryLimit
        historyLimitCallNotify = settings.callNotifyHistoryLimit
    }

    // Detail Dialog State
    var detailSmsMsg by remember { mutableStateOf<ReadRecordData?>(null) }

    // Logic to toggle selection mode
    fun toggleSelection(id: Long) {
        selectedIds = toggleSelectionId(selectedIds, id)
        if (selectedIds.isEmpty()) {
            isSelectionMode = false
        }
    }

    // Back Handler
    BackHandler(enabled = recordSelectionBackEnabled(isActive, isSelectionMode)) {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    val deleteAndUndo: (ReadRecordData) -> Unit = { target ->
        deleteRecordsWithUndo(viewModel, scope, context, snackbarHostState, listOf(target))
    }

    fun deleteSelected() {
        val deleteList = queryState.recordsForTab(selectedRecordTab).filter { sms -> selectedIds.contains(sms.id) }
        if (deleteList.isEmpty()) return
        isSelectionMode = false
        selectedIds = emptySet()
        deleteRecordsWithUndo(viewModel, scope, context, snackbarHostState, deleteList)
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

    val currentTabName = stringResource(recordTabNameRes(selectedRecordTab))
    val currentRecordEnabled = when (selectedRecordTab) {
        0 -> codeRecordEnabled
        1 -> plainRecordEnabled
        2 -> appNotifyRecordEnabled
        else -> callNotifyRecordEnabled
    }
    val currentHistoryLimit = when (selectedRecordTab) {
        0 -> historyLimitCode
        1 -> historyLimitPlain
        2 -> historyLimitAppNotify
        else -> historyLimitCallNotify
    }
    val chartState by viewModel.chartState.collectAsStateWithLifecycle()
    val analyticsEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE_ANALYTICS, true, isActive = isActive)
    val analyticsAllowed = MagiskOtelBootstrap.isEffectivelyEnabled(analyticsEnabled.value)
    LaunchedEffect(showChartSheet, analyticsAllowed) {
        if (showChartSheet) viewModel.openChart(analyticsAllowed)
    }

    fun assignHistoryLimit(value: String) {
        when (selectedRecordTab) {
            0 -> historyLimitCode = value
            1 -> historyLimitPlain = value
            2 -> historyLimitAppNotify = value
            else -> historyLimitCallNotify = value
        }
    }

    fun persistHistoryLimit(value: String) {
        scope.launch {
            settingsRepository.updateRecordSettings(recordLimitUpdate(selectedRecordTab, value))
        }
    }

    RecordSettingsSheet(
        show = showSettingsSheet,
        onDismiss = { showSettingsSheet = false },
        selectedRecordTab = selectedRecordTab,
        currentRecordEnabled = currentRecordEnabled,
        currentTabName = currentTabName,
        currentHistoryLimit = currentHistoryLimit,
        hasRecordsForTab = queryState.recordsForTab(selectedRecordTab).isNotEmpty(),
        savedSnackbarText = savedSnackbarText,
        settingsRepository = settingsRepository,
        scope = scope,
        snackbarHostState = snackbarHostState,
        onRecordEnabledChange = { enabled ->
            when (selectedRecordTab) {
                0 -> codeRecordEnabled = enabled
                1 -> plainRecordEnabled = enabled
                2 -> appNotifyRecordEnabled = enabled
                else -> callNotifyRecordEnabled = enabled
            }
        },
        onShowHistoryLimitDialog = { showHistoryLimitDialog = true },
        onShowClearDialog = { showClearDialog = true },
        onShowExportDialog = {
            exportScope = RecordExportScope.CURRENT_TAB
            showExportDialog = true
        },
        onShowChart = {
            showSettingsSheet = false
            showChartSheet = true
        },
    )

    RecordChartSheet(
        show = showChartSheet,
        onDismiss = { showChartSheet = false },
        chartState = chartState,
        analyticsAllowed = analyticsAllowed,
        onChartTypeChange = viewModel::selectChartType,
        onChartWindowChange = { viewModel.selectChartWindow(it, analyticsAllowed) },
    )

    if (showHistoryLimitDialog) {
        RecordHistoryLimitDialog(
            currentHistoryLimit = currentHistoryLimit,
            onDismiss = { showHistoryLimitDialog = false },
            onCustomLimitRequested = { showHistoryLimitInput = true },
            onLimitSelected = { value ->
                assignHistoryLimit(value)
                persistHistoryLimit(value)
            },
        )
    }

    if (showHistoryLimitInput) {
        RecordHistoryLimitInputDialog(
            currentHistoryLimit = currentHistoryLimit,
            onDismiss = { showHistoryLimitInput = false },
            onLimitSubmitted = { value ->
                if (value.all { it.isDigit() } && value.isNotEmpty()) {
                    assignHistoryLimit(value)
                    persistHistoryLimit(value)
                }
            },
        )
    }

    if (showExportDialog) {
        RecordExportDialog(
            selectedRecordTab = selectedRecordTab,
            exportScope = exportScope,
            onDismiss = { showExportDialog = false },
            onExportConfirmed = { confirmedScope, confirmedTab, filename ->
                exportScope = confirmedScope
                pendingExportScope = confirmedScope
                pendingExportTab = confirmedTab
                showExportDialog = false
                exportLauncher.launch(filename)
            },
        )
    }

    val navigationBarPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val effectiveBottomPadding = maxOf(bottomContentPadding, navigationBarPadding)
    val recordListState = rememberLazyListState()

    val codeSmsList = queryState.codeRecords
    val plainSmsList = queryState.plainSmsRecords
    val appNotifyList = queryState.appNotifyRecords
    val callNotifyList = queryState.callNotifyRecords
    val activeSmsList = queryState.recordsForTab(selectedRecordTab)

    LaunchedEffect(
        isActive,
        activeSmsList,
        selectedRecordTab,
        defaultSmsPackage,
        defaultDialerPackage,
        targetRecordIconPx,
    ) {
        if (!isActive) return@LaunchedEffect
        val packages = buildList {
            if (selectedRecordTab == 0) {
                addAll(activeSmsList.mapNotNull { it.packageName?.takeIf(String::isNotBlank) })
            }
            add(defaultSmsPackage)
            add(defaultDialerPackage)
            addAll(activeSmsList.mapNotNull { record ->
                record.packageName?.takeIf(String::isNotBlank)
                    ?: when (record.msgType) {
                        ReadRecordData.MSG_TYPE_CALL_NOTIFY -> defaultDialerPackage
                        ReadRecordData.MSG_TYPE_SMS -> defaultSmsPackage
                        else -> null
                    }
            })
        }
        viewModel.preloadRecordIcons(packages, targetRecordIconPx)
    }
    val activeTitle = context.getString(recordColumnTitleRes(selectedRecordTab))
    val activeEmptyHint = when (selectedRecordTab) {
        0 -> context.getString(R.string.records_column_code_empty)
        1 -> context.getString(R.string.records_column_plain_empty)
        2 -> context.getString(R.string.records_column_app_notify_empty)
        else -> context.getString(R.string.records_column_call_notify_empty)
    }

    if (showClearDialog) {
        RecordClearDialog(
            currentTabName = stringResource(recordTabNameRes(selectedRecordTab)),
            onDismiss = { showClearDialog = false },
            onConfirm = {
                deleteRecordsWithUndo(viewModel, scope, context, snackbarHostState, activeSmsList.toList())
                showClearDialog = false
            },
        )
    }

    // No explicit background: the theme's own backdrop shows through, matching XSC
    // (a hard-coded M3 background token would fight the miuix colour scheme).
    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        val chromeTitle = if (isSelectionMode) {
            stringResource(R.string.selected_count, selectedIds.size)
        } else {
            stringResource(R.string.tab_records)
        }
        val recordTabRow: @Composable () -> Unit = {
            RecordTabRow(
                visible = smsList.isNotEmpty(),
                tabCounts = listOf(
                    codeSmsList.size,
                    plainSmsList.size,
                    appNotifyList.size,
                    callNotifyList.size,
                ),
                selectedRecordTab = selectedRecordTab,
                onSelectTab = { selectedRecordTab = it },
            )
        }
        val recordBody: @Composable (PaddingValues, Modifier) -> Unit =
            { listPadding, listScrollModifier ->
                AppPullToRefresh(
                    isRefreshing = manualRefreshing,
                    onRefresh = {
                        if (isActive) {
                            manualRefreshStartedAt = SystemClock.elapsedRealtime()
                            manualRefreshing = true
                            viewModel.refreshData()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = listPadding.calculateTopPadding(),
                    ),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when {
                            showLoading && !manualRefreshing && smsList.isEmpty() -> {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    PolygonMorphLoadingIndicator(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(
                                                top = listPadding.calculateTopPadding() +
                                                    LoadingIndicatorTokens.OverlayTopSpacing,
                                            ),
                                    )
                                }
                            }
                            activeSmsList.isEmpty() && !showLoading -> {
                                WorkspaceEmptyState(
                                    title = stringResource(R.string.list_empty_prompt),
                                    summary = activeEmptyHint,
                                    modifier = Modifier.fillMaxSize(),
                                    icon = {
                                        AppIcon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = appColor(AppColorRole.OnSurfaceVariant),
                                        )
                                    },
                                )
                            }
                            else -> {
                                RecordSplitColumn(
                                    title = activeTitle,
                                    emptyHint = activeEmptyHint,
                                    list = activeSmsList,
                                    recordTab = selectedRecordTab,
                                    packageLabels = packageLabels,
                                    recordIcons = recordIcons,
                                    defaultSmsPackage = defaultSmsPackage,
                                    defaultDialerPackage = defaultDialerPackage,
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
                                            copyWithFeedback("sms_code", code, message)
                                        }
                                    },
                                    onShowDetail = { detailSmsMsg = it },
                                    onDelete = { deleteAndUndo(it) },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    listScrollModifier = listScrollModifier,
                                    listState = recordListState,
                                    showHeader = false,
                                    listContentPadding = listPadding,
                                    scrollChromeState = scrollChromeState,
                                    isActive = isActive,
                                    scrollToTopSignal = refreshTrigger,
                                    benchmarkTagsEnabled = benchmarkTagsEnabled,
                                )
                            }
                        }
                    }
                }
            }

        CodeRecordScreenChrome(
            title = chromeTitle,
            isSelectionMode = isSelectionMode,
            onBack = onBack,
            onExitSelectionMode = {
                isSelectionMode = false
                selectedIds = emptySet()
            },
            onSelectAllVisible = {
                val visibleIds = activeSmsList.mapNotNull { it.id }.toSet()
                if (visibleIds.isNotEmpty()) {
                    val allVisibleSelected = visibleIds.all { selectedIds.contains(it) }
                    selectedIds = if (allVisibleSelected) {
                        selectedIds - visibleIds
                    } else {
                        selectedIds + visibleIds
                    }
                }
            },
            onDeleteSelected = { deleteSelected() },
            onOpenSettings = { showSettingsSheet = true },
            tabRow = recordTabRow,
            scrollChromeState = scrollChromeState,
            listState = recordListState,
            bottomContentPadding = effectiveBottomPadding,
            body = recordBody,
        )

        val sms = detailSmsMsg
        if (sms != null) {
            RecordDetailOverlay(
                sms = sms,
                packageLabels = packageLabels,
                onDismiss = { detailSmsMsg = null },
                onCopy = { label, value, message ->
                    copyWithFeedback(label, value, message)
                },
                onDelete = {
                    deleteAndUndo(sms)
                },
                onRefund = {
                    viewModel.refund(context, sms)
                    val message = if (sms.msgType == ReadRecordData.MSG_TYPE_APP_NOTIFY) {
                        context.getString(R.string.prompt_notification_refunded)
                    } else {
                        context.getString(R.string.prompt_sms_refunded)
                    }
                    scope.launch { snackbarHostState.showLatestSnackbar(message) }
                },
            )
        }

        io.github.magisk317.uikit.common.AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .padding(bottom = effectiveBottomPadding),
        )
    }
}
