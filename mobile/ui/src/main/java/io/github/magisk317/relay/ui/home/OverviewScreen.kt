package io.github.magisk317.relay.ui.home

import android.os.SystemClock
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.relay.android.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.relay.android.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.relay.common.utils.PackageUtils
import io.github.magisk317.relay.common.utils.Utils
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.engine.service.RuntimeAnalyticsProvider
import io.github.magisk317.relay.contract.settings.OverviewSettingsUpdate
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val CARD_STATUS = "status"
private const val CARD_CHART = "chart"
private const val CARD_APP_INFO = "app_info"
private const val CARD_DEVICE_INFO = "device_info"
private const val CARD_LINKS = "links"

internal data class HomeCardSpec(
    val id: String,
    val titleRes: Int,
    val icon: ImageVector,
    val enabledByDefault: Boolean = true,
    val available: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(hazeState: HazeState, hazeStyle: HazeStyle) {
    val context = LocalContext.current
    val settingsRepository: SettingsPreferencesRepository = koinInject()
    val analyticsRepository: RuntimeAnalyticsProvider = koinInject()
    val settingsViewModel = rememberSharedSettingsViewModel()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    fun showMessage(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }
    var showDonateDialog by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var showAddCardSheet by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var draggingCardId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    val analyticsEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE_ANALYTICS, true)

    val isEnabled = ActivationDiagnosticsStore.isModuleActivated(context)
    val runtimeConnected = ActivationDiagnosticsStore.isRuntimeConnected()
    var statusTapCount by remember { mutableStateOf(0) }
    var statusTapStartedAtMs by remember { mutableStateOf(0L) }
    var showStatusDiagnostics by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val density = LocalDensity.current
    val dragThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    val frameworkInfoState by produceState<Pair<String, String>?>(
        initialValue = null,
    ) {
        value = withContext(Dispatchers.IO) {
            PackageUtils.getLsposedModuleInfo(context)
        }
    }
    val frameworkType = frameworkInfoState?.first ?: stringResource(id = R.string.unknown)
    val frameworkVersion = frameworkInfoState?.second ?: run {
        val lsposedVersion = PackageUtils.getPackageVersion(context, Const.LSPOSED_MANAGER_PACKAGE_NAME)
        when {
            lsposedVersion != null && lsposedVersion.first.isNotBlank() ->
                "${lsposedVersion.first} (${lsposedVersion.second})"

            PackageUtils.isPackageInstalled(context, Const.LSPOSED_MANAGER_PACKAGE_NAME) ->
                stringResource(id = R.string.unknown)

            else -> stringResource(id = R.string.not_installed)
        }
    }
    val activationDiagnostics by produceState(
        initialValue = ActivationDiagnosticsStore.snapshot(context),
        context,
    ) {
        while (true) {
            value = ActivationDiagnosticsStore.snapshot(context)
            delay(1500L)
        }
    }
    val hasRootAccessState by produceState(
        initialValue = false,
    ) {
        value = withContext(Dispatchers.IO) {
            PackageUtils.hasRootAccess()
        }
    }
    val appVersionState by produceState<Pair<String, Long>?>(
        initialValue = null,
    ) {
        value = withContext(Dispatchers.IO) {
            PackageUtils.getPackageVersion(context, context.packageName)
        }
    }
    val appVersionName = appVersionState?.first?.takeIf { it.isNotBlank() } ?: stringResource(id = R.string.unknown)
    val appVersionCode = appVersionState?.second?.toString() ?: stringResource(id = R.string.unknown)

    val allCardSpecs = listOf(
        HomeCardSpec(
            id = CARD_STATUS,
            titleRes = R.string.home_card_status_title,
            icon = Icons.Default.CheckCircle,
        ),
        HomeCardSpec(
            id = CARD_APP_INFO,
            titleRes = R.string.home_card_appinfo_title,
            icon = Icons.Default.Build,
        ),
        HomeCardSpec(
            id = CARD_DEVICE_INFO,
            titleRes = R.string.home_card_deviceinfo_title,
            icon = Icons.Default.Phone,
        ),
        HomeCardSpec(
            id = CARD_LINKS,
            titleRes = R.string.home_card_links_title,
            icon = Icons.Default.Favorite,
        ),
        HomeCardSpec(
            id = CARD_CHART,
            titleRes = R.string.home_card_chart_title,
            icon = Icons.AutoMirrored.Filled.List,
            available = analyticsEnabled.value,
        ),
    )
    val cardSpecById = remember(allCardSpecs) { allCardSpecs.associateBy(HomeCardSpec::id) }

    val defaultCardOrder = remember(allCardSpecs) { allCardSpecs.map(HomeCardSpec::id) }
    val defaultEnabled = remember(allCardSpecs) {
        allCardSpecs.filter(HomeCardSpec::enabledByDefault).map(HomeCardSpec::id).toSet()
    }
    var cardOrder by remember { mutableStateOf(defaultCardOrder) }
    var enabledCardIds by remember { mutableStateOf(defaultEnabled) }

    var chartType by remember { mutableStateOf(HomeChartType.EVENTS) }
    var chartWindow by remember { mutableStateOf(HomeChartWindow.ALL) }

    LaunchedEffect(Unit) {
        val overviewSettings = settingsRepository.getOverviewSettings()
        val storedOrder = overviewSettings.cardOrder
        val parsedOrder = if (storedOrder.isBlank()) defaultCardOrder else parseCardList(storedOrder)
        val normalizedOrder = normalizeCardOrder(parsedOrder, defaultCardOrder)
        cardOrder = normalizedOrder
        if (normalizedOrder != parsedOrder || storedOrder.isBlank()) {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(cardOrder = encodeCardList(normalizedOrder)),
            )
        }

        val storedEnabled = overviewSettings.enabledCardIds
        val parsedEnabled = if (storedEnabled.isBlank()) defaultEnabled else parseCardList(storedEnabled).toSet()
        val normalizedEnabled = normalizeCardEnabled(parsedEnabled, defaultEnabled)
        enabledCardIds = normalizedEnabled
        if (normalizedEnabled != parsedEnabled || storedEnabled.isBlank()) {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(enabledCardIds = encodeCardList(normalizedEnabled.toList())),
            )
        }

        val storedChartType = overviewSettings.chartType
        val parsedChartType = HomeChartType.fromId(storedChartType) ?: HomeChartType.EVENTS
        chartType = parsedChartType
        if (storedChartType.isBlank() || parsedChartType.id != storedChartType) {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(chartType = parsedChartType.id),
            )
        }

        val storedChartWindow = overviewSettings.chartWindow
        val parsedChartWindow = HomeChartWindow.fromId(storedChartWindow) ?: HomeChartWindow.ALL
        chartWindow = parsedChartWindow
        if (storedChartWindow.isBlank() || parsedChartWindow.id != storedChartWindow) {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(chartWindow = parsedChartWindow.id),
            )
        }
    }

    val chartSnapshot by produceState<HomeAnalyticsSnapshot?>(
        initialValue = null,
        key1 = chartWindow,
        key2 = analyticsEnabled.value,
    ) {
        if (!analyticsEnabled.value) {
            value = null
            return@produceState
        }
        val nowMs = System.currentTimeMillis()
        val fromMs = chartWindow.fromMs(nowMs)
        val snapshot = analyticsRepository.snapshot(fromMs)
        value = HomeAnalyticsSnapshot(
            totalMessages = snapshot.totalMessages,
            codeDetected = snapshot.codeDetected,
            autoInputAttempt = snapshot.autoInputAttempt,
            autoInputSuccess = snapshot.autoInputSuccess,
            autoInputFailed = snapshot.autoInputFailed,
            forwardTotal = snapshot.forwardTotal,
            forwardSuccess = snapshot.forwardSuccess,
            forwardFailed = snapshot.forwardFailed,
            senderStats = snapshot.senderStats,
        )
    }

    val visibleCardIds = remember(cardOrder, enabledCardIds, analyticsEnabled.value) {
        cardOrder.filter { id ->
            enabledCardIds.contains(id) && (cardSpecById[id]?.available == true)
        }
    }
    val visibleCardSpecs = remember(visibleCardIds, cardSpecById) {
        visibleCardIds.mapNotNull { cardSpecById[it] }
    }
    val addableCardSpecs = remember(allCardSpecs, enabledCardIds) {
        allCardSpecs.filter { spec ->
            spec.available && !enabledCardIds.contains(spec.id)
        }
    }

    fun persistCardOrder(newOrder: List<String>) {
        coroutineScope.launch {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(cardOrder = encodeCardList(newOrder)),
            )
        }
    }

    fun persistEnabledCardIds(newEnabled: Set<String>) {
        coroutineScope.launch {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(enabledCardIds = encodeCardList(newEnabled.toList())),
            )
        }
    }

    fun exitEditMode() {
        showAddCardSheet = false
        editMode = false
        draggingCardId = null
        dragOffsetY = 0f
    }

    fun addCard(cardId: String) {
        val oldOrder = cardOrder
        val newEnabled = enabledCardIds + cardId
        val nextOrder = if (oldOrder.contains(cardId)) {
            oldOrder
        } else {
            normalizeCardOrder(oldOrder + cardId, defaultCardOrder)
        }
        enabledCardIds = newEnabled
        cardOrder = nextOrder
        coroutineScope.launch {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(
                    enabledCardIds = encodeCardList(newEnabled.toList()),
                    cardOrder = if (nextOrder != oldOrder) encodeCardList(nextOrder) else null,
                ),
            )
        }
    }

    OverviewContent(
        hazeState = hazeState,
        hazeStyle = hazeStyle,
        listState = listState,
        scrollBehavior = scrollBehavior,
        visibleCardSpecs = visibleCardSpecs,
        cardOrder = cardOrder,
        enabledCardIds = enabledCardIds,
        editMode = editMode,
        isEnabled = isEnabled,
        chartType = chartType,
        chartWindow = chartWindow,
        chartSnapshot = chartSnapshot,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        frameworkType = frameworkType,
        frameworkVersion = frameworkVersion,
        hasRootAccess = hasRootAccessState,
        runtimeConnected = runtimeConnected,
        activationDiagnostics = activationDiagnostics,
        showStatusDiagnostics = showStatusDiagnostics,
        draggingCardId = draggingCardId,
        dragOffsetY = dragOffsetY,
        dragThresholdPx = dragThresholdPx,
        showAddAction = editMode && addableCardSpecs.isNotEmpty(),
        onToggleEditMode = {
            if (editMode) {
                exitEditMode()
            } else {
                editMode = true
            }
        },
        onShowAddSheet = { showAddCardSheet = true },
        onRequestEnableEditMode = {},
        onCardOrderChange = { newOrder -> cardOrder = newOrder },
        onPersistCardOrder = ::persistCardOrder,
        onEnabledCardIdsChange = { newEnabled -> enabledCardIds = newEnabled },
        onPersistEnabledCardIds = ::persistEnabledCardIds,
        onDragStateChange = { nextCardId, nextOffsetY ->
            draggingCardId = nextCardId
            dragOffsetY = nextOffsetY
        },
        onChartTypeChange = { next ->
            chartType = next
            coroutineScope.launch {
                settingsRepository.updateOverviewSettings(
                    OverviewSettingsUpdate(chartType = next.id),
                )
            }
        },
        onChartWindowChange = { next ->
            chartWindow = next
            coroutineScope.launch {
                settingsRepository.updateOverviewSettings(
                    OverviewSettingsUpdate(chartWindow = next.id),
                )
            }
        },
        onCheckUpdate = { settingsViewModel.requestPreferredUpdate() },
        onShowDonate = { showDonateDialog = true },
        onStatusCardTap = {
            val now = SystemClock.uptimeMillis()
            val withinWindow = now - statusTapStartedAtMs <= 1800L
            statusTapCount = if (withinWindow) statusTapCount + 1 else 1
            statusTapStartedAtMs = now
            if (statusTapCount >= 5) {
                showStatusDiagnostics = !showStatusDiagnostics
                statusTapCount = 0
                statusTapStartedAtMs = 0L
                showMessage(
                    if (showStatusDiagnostics) {
                        context.getString(R.string.overview_runtime_diagnostics_shown)
                    } else {
                        context.getString(R.string.overview_runtime_diagnostics_hidden)
                    },
                )
            }
        },
    )

    OverviewDialogs(
        context = context,
        showAddCardSheet = showAddCardSheet,
        addableCardSpecs = addableCardSpecs,
        showDonateDialog = showDonateDialog,
        showQRCodeDialog = showQRCodeDialog,
        onDismissAddSheet = { showAddCardSheet = false },
        onAddCard = ::addCard,
        onToggleDonateDialog = { showDonateDialog = it },
        onShowQrCodeDialog = { showQRCodeDialog = it },
        onShowMessage = ::showMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewContent(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    visibleCardSpecs: List<HomeCardSpec>,
    cardOrder: List<String>,
    enabledCardIds: Set<String>,
    editMode: Boolean,
    isEnabled: Boolean,
    chartType: HomeChartType,
    chartWindow: HomeChartWindow,
    chartSnapshot: HomeAnalyticsSnapshot?,
    appVersionName: String,
    appVersionCode: String,
    frameworkType: String,
    frameworkVersion: String,
    hasRootAccess: Boolean,
    runtimeConnected: Boolean,
    activationDiagnostics: ActivationDiagnosticsSnapshot,
    showStatusDiagnostics: Boolean,
    draggingCardId: String?,
    dragOffsetY: Float,
    dragThresholdPx: Float,
    showAddAction: Boolean,
    onToggleEditMode: () -> Unit,
    onShowAddSheet: () -> Unit,
    onRequestEnableEditMode: () -> Unit,
    onCardOrderChange: (List<String>) -> Unit,
    onPersistCardOrder: (List<String>) -> Unit,
    onEnabledCardIdsChange: (Set<String>) -> Unit,
    onPersistEnabledCardIds: (Set<String>) -> Unit,
    onDragStateChange: (String?, Float) -> Unit,
    onChartTypeChange: (HomeChartType) -> Unit,
    onChartWindowChange: (HomeChartWindow) -> Unit,
    onCheckUpdate: () -> Unit,
    onShowDonate: () -> Unit,
    onStatusCardTap: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp),
            state = listState,
            userScrollEnabled = draggingCardId == null,
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp + 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(visibleCardSpecs, key = { it.id }) { spec ->
                OverviewCardItem(
                    context = context,
                    spec = spec,
                    visibleCardIds = visibleCardSpecs.map { it.id },
                    cardOrder = cardOrder,
                    enabledCardIds = enabledCardIds,
                    editMode = editMode,
                    isEnabled = isEnabled,
                    chartType = chartType,
                    chartWindow = chartWindow,
                    chartSnapshot = chartSnapshot,
                    appVersionName = appVersionName,
                    appVersionCode = appVersionCode,
                    frameworkType = frameworkType,
                    frameworkVersion = frameworkVersion,
                    hasRootAccess = hasRootAccess,
                    runtimeConnected = runtimeConnected,
                    activationDiagnostics = activationDiagnostics,
                    showStatusDiagnostics = showStatusDiagnostics,
                    draggingCardId = draggingCardId,
                    dragOffsetY = dragOffsetY,
                    dragThresholdPx = dragThresholdPx,
                    onRequestEnableEditMode = onRequestEnableEditMode,
                    onCardOrderChange = onCardOrderChange,
                    onPersistCardOrder = onPersistCardOrder,
                    onEnabledCardIdsChange = onEnabledCardIdsChange,
                    onPersistEnabledCardIds = onPersistEnabledCardIds,
                    onDragStateChange = onDragStateChange,
                    onChartTypeChange = onChartTypeChange,
                    onChartWindowChange = onChartWindowChange,
                    onCheckUpdate = onCheckUpdate,
                    onShowDonate = onShowDonate,
                    onStatusCardTap = onStatusCardTap,
                )
            }
        }

        TopAppBar(
            title = { Text(text = stringResource(id = R.string.app_name)) },
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets.statusBars,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .hazeEffect(hazeState, hazeStyle) {
                    forceInvalidateOnPreDraw = true
                },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            actions = {
                if (showAddAction) {
                    IconButton(onClick = onShowAddSheet) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(id = R.string.forward_filter_action_add),
                        )
                    }
                }
                IconButton(onClick = onToggleEditMode) {
                    Icon(
                        imageVector = if (editMode) Icons.Default.Done else Icons.Default.Edit,
                        contentDescription = if (editMode) {
                            stringResource(id = R.string.action_rule_edit_done)
                        } else {
                            stringResource(id = R.string.edit)
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun OverviewCardItem(
    context: android.content.Context,
    spec: HomeCardSpec,
    visibleCardIds: List<String>,
    cardOrder: List<String>,
    enabledCardIds: Set<String>,
    editMode: Boolean,
    isEnabled: Boolean,
    chartType: HomeChartType,
    chartWindow: HomeChartWindow,
    chartSnapshot: HomeAnalyticsSnapshot?,
    appVersionName: String,
    appVersionCode: String,
    frameworkType: String,
    frameworkVersion: String,
    hasRootAccess: Boolean,
    runtimeConnected: Boolean,
    activationDiagnostics: ActivationDiagnosticsSnapshot,
    showStatusDiagnostics: Boolean,
    draggingCardId: String?,
    dragOffsetY: Float,
    dragThresholdPx: Float,
    onRequestEnableEditMode: () -> Unit,
    onCardOrderChange: (List<String>) -> Unit,
    onPersistCardOrder: (List<String>) -> Unit,
    onEnabledCardIdsChange: (Set<String>) -> Unit,
    onPersistEnabledCardIds: (Set<String>) -> Unit,
    onDragStateChange: (String?, Float) -> Unit,
    onChartTypeChange: (HomeChartType) -> Unit,
    onChartWindowChange: (HomeChartWindow) -> Unit,
    onCheckUpdate: () -> Unit,
    onShowDonate: () -> Unit,
    onStatusCardTap: () -> Unit,
) {
    val dragEnabled = editMode
    HomeCardContainer(
        editMode = editMode,
        wiggleKey = spec.id,
        isDragging = draggingCardId == spec.id,
        dragOffsetY = if (draggingCardId == spec.id) dragOffsetY else 0f,
        onLongPress = {},
        onRemove = {
            val newEnabled = enabledCardIds - spec.id
            onEnabledCardIdsChange(newEnabled)
            onPersistEnabledCardIds(newEnabled)
        },
        onDragStart = if (dragEnabled) { { onDragStateChange(spec.id, 0f) } } else null,
        onDragEnd = if (dragEnabled) { { onDragStateChange(null, 0f) } } else null,
        onDrag = if (dragEnabled) {
            { deltaY: Float ->
                if (draggingCardId != spec.id) return@HomeCardContainer
                val nextOffset = dragOffsetY + deltaY
                val canMoveUp = visibleCardIds.indexOf(spec.id) > 0
                val canMoveDown = visibleCardIds.indexOf(spec.id) < visibleCardIds.lastIndex
                when {
                    nextOffset <= -dragThresholdPx && canMoveUp -> {
                        val newOrder = moveCardByVisible(cardOrder, visibleCardIds, spec.id, -1)
                        onCardOrderChange(newOrder)
                        onPersistCardOrder(newOrder)
                        onDragStateChange(spec.id, nextOffset + dragThresholdPx)
                    }
                    nextOffset >= dragThresholdPx && canMoveDown -> {
                        val newOrder = moveCardByVisible(cardOrder, visibleCardIds, spec.id, 1)
                        onCardOrderChange(newOrder)
                        onPersistCardOrder(newOrder)
                        onDragStateChange(spec.id, nextOffset - dragThresholdPx)
                    }
                    else -> onDragStateChange(spec.id, nextOffset)
                }
            }
        } else {
            null
        },
    ) {
        when (spec.id) {
            CARD_STATUS -> {
                StatusCard(
                    isEnabled = isEnabled,
                    showDiagnostics = showStatusDiagnostics,
                    diagnostics = buildStatusDiagnostics(
                        context = context,
                        snapshot = activationDiagnostics,
                        runtimeConnected = runtimeConnected,
                    ),
                    onClick = if (editMode) {
                        null
                    } else {
                        onStatusCardTap
                    },
                )
            }
            CARD_CHART -> {
                HomeChartCard(
                    chartType = chartType,
                    chartWindow = chartWindow,
                    data = chartSnapshot,
                    onChartTypeChange = if (editMode) {
                        {}
                    } else {
                        onChartTypeChange
                    },
                    onChartWindowChange = if (editMode) {
                        {}
                    } else {
                        onChartWindowChange
                    },
                )
            }
            CARD_APP_INFO -> {
                AppInfoCard(
                    appVersionName = appVersionName,
                    appVersionCode = appVersionCode,
                    frameworkType = frameworkType,
                    frameworkVersion = frameworkVersion,
                    hasRootAccess = hasRootAccess,
                    interactive = !editMode,
                )
            }
            CARD_DEVICE_INFO -> DeviceInfoCard()
            CARD_LINKS -> {
                LinksCard(
                    onCheckUpdate = if (editMode) {
                        {}
                    } else {
                        onCheckUpdate
                    },
                    onDonate = if (editMode) {
                        {}
                    } else {
                        onShowDonate
                    },
                    interactive = !editMode,
                )
            }
        }
    }
}

@Composable
private fun OverviewDialogs(
    context: android.content.Context,
    showAddCardSheet: Boolean,
    addableCardSpecs: List<HomeCardSpec>,
    showDonateDialog: Boolean,
    showQRCodeDialog: Pair<Int, String>?,
    onDismissAddSheet: () -> Unit,
    onAddCard: (String) -> Unit,
    onToggleDonateDialog: (Boolean) -> Unit,
    onShowQrCodeDialog: (Pair<Int, String>?) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    if (showAddCardSheet) {
        AddOverviewCardSheet(
            specs = addableCardSpecs,
            onDismiss = onDismissAddSheet,
            onAdd = onAddCard,
        )
    }

    if (showDonateDialog) {
        DonateDialog(
            onDismiss = { onToggleDonateDialog(false) },
            onAlipay = {
                onToggleDonateDialog(false)
                onShowQrCodeDialog(Pair(R.drawable.alipay, "alipay"))
            },
            onWechat = {
                onToggleDonateDialog(false)
                onShowQrCodeDialog(Pair(R.drawable.wx, "wechat"))
            },
        )
    }

    showQRCodeDialog?.let { pair ->
        QRCodeDialog(
            resId = pair.first,
            type = pair.second,
            onDismiss = { onShowQrCodeDialog(null) },
            onSave = {
                Utils.saveImageToGallery(context, pair.first, "${pair.second}_qrcode")
                    .forEach(onShowMessage)
            },
        )
    }
}

private fun parseCardList(value: String): List<String> =
    value.split(',').map { it.trim() }.filter { it.isNotBlank() }

private fun encodeCardList(items: List<String>): String = items.joinToString(",")

private fun normalizeCardOrder(order: List<String>, fallback: List<String>): List<String> {
    val known = fallback.toSet()
    val filtered = order.filter { known.contains(it) }.distinct()
    val missing = fallback.filterNot { filtered.contains(it) }
    return filtered + missing
}

private fun normalizeCardEnabled(enabled: Set<String>, fallback: Set<String>): Set<String> {
    val normalized = enabled.filter { fallback.contains(it) }.toSet()
    return if (normalized.isEmpty()) fallback else normalized
}

private fun moveCardByVisible(order: List<String>, visible: List<String>, cardId: String, direction: Int): List<String> {
    val indexInVisible = visible.indexOf(cardId)
    if (indexInVisible == -1) return order
    val targetIndex = indexInVisible + direction
    if (targetIndex !in visible.indices) return order
    val targetId = visible[targetIndex]
    val mutable = order.toMutableList()
    mutable.remove(cardId)
    val insertIndex = mutable.indexOf(targetId).let { if (direction > 0) it + 1 else it }
    mutable.add(insertIndex, cardId)
    return mutable
}
