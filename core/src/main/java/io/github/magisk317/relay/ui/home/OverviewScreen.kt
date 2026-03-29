package io.github.magisk317.relay.ui.home

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.magisk317.relay.common.constant.Const
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.relay.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.relay.common.utils.FrameworkCompatibilityMonitor
import io.github.magisk317.relay.common.utils.PackageUtils
import io.github.magisk317.relay.common.utils.Utils
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.data.db.dao.SenderDispatchStatRow
import io.github.magisk317.relay.data.repository.AnalyticsRepository
import io.github.magisk317.relay.data.repository.OverviewSettingsUpdate
import io.github.magisk317.relay.data.repository.SettingsRepository
import io.github.magisk317.relay.ui.sender.getSenderTypeName
import io.github.magisk317.relay.ui.common.SegmentedOption
import io.github.magisk317.relay.ui.common.SingleChoiceSegmentedSelector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val CARD_STATUS = "status"
private const val CARD_CHART = "chart"
private const val CARD_APP_INFO = "app_info"
private const val CARD_DEVICE_INFO = "device_info"
private const val CARD_LINKS = "links"
private const val FRAMEWORK_MONITOR_REFRESH_INTERVAL_MS = 1_500L

private data class HomeAnalyticsSnapshot(
    val totalMessages: Long,
    val codeDetected: Long,
    val autoInputAttempt: Long,
    val autoInputSuccess: Long,
    val autoInputFailed: Long,
    val forwardTotal: Long,
    val forwardSuccess: Long,
    val forwardFailed: Long,
    val senderStats: List<SenderDispatchStatRow>,
)

private enum class HomeChartType(val id: String) {
    FORWARD("forward"),
    EVENTS("events"),
    SENDER("sender"),
    ;

    fun labelRes(): Int = when (this) {
        FORWARD -> R.string.home_chart_type_forward
        EVENTS -> R.string.home_chart_type_events
        SENDER -> R.string.home_chart_type_sender
    }

    companion object {
        fun fromId(id: String?): HomeChartType? = values().firstOrNull { it.id == id }
    }
}

private enum class HomeChartWindow(val id: String, val days: Int?) {
    ALL("all", null),
    LAST_7_DAYS("7d", 7),
    LAST_30_DAYS("30d", 30),
    ;

    fun labelRes(): Int = when (this) {
        ALL -> R.string.home_chart_window_all
        LAST_7_DAYS -> R.string.home_chart_window_7d
        LAST_30_DAYS -> R.string.home_chart_window_30d
    }

    fun fromMs(nowMs: Long): Long {
        val limit = days ?: return 0L
        return nowMs - limit * 24L * 60L * 60L * 1000L
    }

    companion object {
        fun fromId(id: String?): HomeChartWindow? = values().firstOrNull { it.id == id }
    }
}

private data class PieSlice(
    val label: String,
    val value: Long,
    val color: Color,
)

private data class HomeCardSpec(
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
    val settingsRepository: SettingsRepository = koinInject()
    val analyticsRepository: AnalyticsRepository = koinInject()
    val settingsViewModel = rememberSharedSettingsViewModel()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    fun showMessage(message: String) {
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }
    var showDonateDialog by remember { mutableStateOf(false) }
    var showAlipayChoiceDialog by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var showAddCardSheet by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var draggingCardId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    val analyticsEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE_ANALYTICS, true)

    val isEnabled = ActivationDiagnosticsStore.isModuleActivated(context)
    val runtimeConnected = ActivationDiagnosticsStore.isRuntimeConnected()
    val frameworkIssue by FrameworkCompatibilityMonitor.issueState.collectAsStateWithLifecycle()
    var statusTapCount by remember { mutableStateOf(0) }
    var statusTapStartedAtMs by remember { mutableStateOf(0L) }
    var showStatusDiagnostics by remember { mutableStateOf(false) }

    FrameworkMonitorEffect()

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val density = LocalDensity.current
    val dragThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    val frameworkInfoState by produceState<Pair<String, String>?>(
        initialValue = null,
    ) {
        value = withContext(Dispatchers.IO) {
            PackageUtils.getLsposedModuleInfo()
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
        frameworkIssue = frameworkIssue,
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
        showAlipayChoiceDialog = showAlipayChoiceDialog,
        showQRCodeDialog = showQRCodeDialog,
        onDismissAddSheet = { showAddCardSheet = false },
        onAddCard = ::addCard,
        onToggleDonateDialog = { showDonateDialog = it },
        onToggleAlipayChoiceDialog = { showAlipayChoiceDialog = it },
        onShowQrCodeDialog = { showQRCodeDialog = it },
        onShowMessage = ::showMessage,
    )
}

@Composable
private fun FrameworkMonitorEffect() {
    LaunchedEffect(Unit) {
        while (true) {
            FrameworkCompatibilityMonitor.refreshFromRuntimeLogs()
            delay(FRAMEWORK_MONITOR_REFRESH_INTERVAL_MS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewContent(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    frameworkIssue: FrameworkCompatibilityMonitor.FrameworkIssue?,
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
            frameworkIssue?.let { issue ->
                item {
                    FrameworkIncompatibilityCard(
                        message = when (issue.issueType) {
                            FrameworkCompatibilityMonitor.FrameworkIssueType.HOOKER_ANNOTATION_INCOMPATIBLE ->
                                stringResource(id = R.string.framework_incompatibility_hooker_annotation_message)
                        },
                    )
                }
            }

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
    showAlipayChoiceDialog: Boolean,
    showQRCodeDialog: Pair<Int, String>?,
    onDismissAddSheet: () -> Unit,
    onAddCard: (String) -> Unit,
    onToggleDonateDialog: (Boolean) -> Unit,
    onToggleAlipayChoiceDialog: (Boolean) -> Unit,
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
                onToggleAlipayChoiceDialog(true)
            },
            onWechat = {
                onToggleDonateDialog(false)
                onShowQrCodeDialog(Pair(R.drawable.wx, "wechat"))
            },
        )
    }

    if (showAlipayChoiceDialog) {
        AlipayChoiceDialog(
            onDismiss = { onToggleAlipayChoiceDialog(false) },
            onQRCode = {
                onToggleAlipayChoiceDialog(false)
                onShowQrCodeDialog(Pair(R.drawable.alipay, "alipay"))
            },
            onToken = {
                onToggleAlipayChoiceDialog(false)
                PackageUtils.copyAlipayPocketToken(context).let(onShowMessage)
                PackageUtils.startAlipayActivity(context)?.let(onShowMessage)
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

private fun openLsposedManager(context: android.content.Context) {
    val intent = Intent().apply {
        setClassName(
            "org.lsposed.manager",
            "org.lsposed.manager.ui.activity.MainActivity",
        )
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (ignored: Exception) {
        // Ignore if LSPosed manager is not installed.
    }
}

@Composable
private fun HomeCardContainer(
    editMode: Boolean,
    wiggleKey: String,
    isDragging: Boolean,
    dragOffsetY: Float,
    onLongPress: () -> Unit,
    onRemove: (() -> Unit)?,
    onDragStart: (() -> Unit)?,
    onDragEnd: (() -> Unit)?,
    onDrag: ((Float) -> Unit)?,
    content: @Composable () -> Unit,
) {
    val wiggleParams = remember(wiggleKey) {
        val random = kotlin.random.Random(wiggleKey.hashCode())
        val amplitudeFactor = 0.7f + random.nextFloat() * 0.3f
        val startOffsetMs = random.nextInt(120)
        WiggleParams(
            amplitudeFactor = amplitudeFactor,
            startOffsetMs = startOffsetMs,
        )
    }
    val wiggleTransition = rememberInfiniteTransition(label = "overviewWiggle")
    val wiggleValue by wiggleTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 120,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(
                wiggleParams.startOffsetMs,
                StartOffsetType.FastForward,
            ),
        ),
        label = "wigglePhase",
    )
    val removeScale = remember { Animatable(1f) }
    val removeAlpha = remember { Animatable(1f) }
    var removing by remember { mutableStateOf(false) }
    val wiggleEnabled = editMode && !isDragging && !removing
    val rotation = if (wiggleEnabled) {
        val degreesPerRad = (180f / PI.toFloat())
        wiggleValue * 0.012f * wiggleParams.amplitudeFactor * degreesPerRad
    } else {
        0f
    }
    val translationY = if (isDragging) dragOffsetY else 0f
    val dragScale = if (isDragging) 1.04f else 1f
    val scale = dragScale * removeScale.value
    val alpha = if (isDragging) 0.85f else removeAlpha.value
    val scope = rememberCoroutineScope()
    val editModeState by rememberUpdatedState(editMode)
    val onLongPressState by rememberUpdatedState(onLongPress)
    val onDragStartState by rememberUpdatedState(onDragStart)
    val onDragEndState by rememberUpdatedState(onDragEnd)
    val onDragState by rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(editMode) {
                if (onDragState != null) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress != null) {
                            if (!editModeState) {
                                onLongPressState()
                            }
                            XLog.i("Overview drag longPress key=%s editMode=%s", wiggleKey, editModeState)
                            onDragStartState?.invoke()
                            var loggedMove = false
                            try {
                                drag(longPress.id) { change ->
                                    val delta = change.positionChange()
                                    if (delta.y != 0f) {
                                        if (!loggedMove) {
                                            loggedMove = true
                                            XLog.i("Overview drag move key=%s dy=%.2f", wiggleKey, delta.y)
                                        }
                                        onDragState?.invoke(delta.y)
                                        change.consume()
                                    }
                                }
                            } finally {
                                XLog.i("Overview drag end key=%s moved=%s", wiggleKey, loggedMove)
                                onDragEndState?.invoke()
                            }
                        }
                    }
                } else {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress != null) {
                            onLongPress()
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                rotationZ = rotation
                this.translationY = translationY
                shadowElevation = if (isDragging) 10f else 0f
            },
    ) {
        content()
        if (editMode && !removing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .shadow(3.dp, CircleShape, clip = false)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(enabled = onRemove != null) {
                        if (removing || onRemove == null) return@clickable
                        removing = true
                        scope.launch {
                            removeScale.snapTo(1f)
                            removeAlpha.snapTo(1f)
                            val spec = tween<Float>(
                                durationMillis = 300,
                                easing = FastOutLinearInEasing,
                            )
                            val scaleJob = launch { removeScale.animateTo(0.4f, spec) }
                            val alphaJob = launch { removeAlpha.animateTo(0f, spec) }
                            scaleJob.join()
                            alphaJob.join()
                            onRemove.invoke()
                        }
                    }
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(id = R.string.remove),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private data class WiggleParams(
    val amplitudeFactor: Float,
    val startOffsetMs: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddOverviewCardSheet(
    specs: List<HomeCardSpec>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.home_add_card_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (specs.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.home_add_card_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(specs, key = { it.id }) { spec ->
                        AddOverviewCardItem(
                            spec = spec,
                            onAdd = { onAdd(spec.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddOverviewCardItem(
    spec: HomeCardSpec,
    onAdd: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(
                        modifier = Modifier.size(42.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = spec.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(id = spec.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(id = R.string.home_add_card_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        FilledTonalIconButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(id = R.string.forward_filter_action_add),
            )
        }
    }
}

@Composable
private fun HomeChartCard(
    chartType: HomeChartType,
    chartWindow: HomeChartWindow,
    data: HomeAnalyticsSnapshot?,
    onChartTypeChange: (HomeChartType) -> Unit,
    onChartWindowChange: (HomeChartWindow) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        HomeChartBody(
            chartType = chartType,
            chartWindow = chartWindow,
            data = data,
            onChartTypeChange = onChartTypeChange,
            onChartWindowChange = onChartWindowChange,
            showTitle = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun HomeChartBody(
    chartType: HomeChartType,
    chartWindow: HomeChartWindow,
    data: HomeAnalyticsSnapshot?,
    onChartTypeChange: (HomeChartType) -> Unit,
    onChartWindowChange: (HomeChartWindow) -> Unit,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showTitle) {
            Text(
                text = stringResource(id = R.string.home_card_chart_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        data?.let {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatLine(label = stringResource(id = R.string.home_event_total_messages), value = data.totalMessages)
                StatLine(label = stringResource(id = R.string.home_event_code_detected), value = data.codeDetected)
                StatLine(label = stringResource(id = R.string.home_event_auto_input_attempt), value = data.autoInputAttempt)
                StatLine(label = stringResource(id = R.string.home_event_auto_input_success), value = data.autoInputSuccess)
                StatLine(label = stringResource(id = R.string.home_event_auto_input_failed), value = data.autoInputFailed)
                StatLine(label = stringResource(id = R.string.home_event_forward_success), value = data.forwardSuccess)
                StatLine(label = stringResource(id = R.string.home_event_forward_failed), value = data.forwardFailed)
            }
        }

        SingleChoiceSegmentedSelector(
            options = HomeChartType.values().map { type ->
                SegmentedOption(type, stringResource(id = type.labelRes()))
            },
            selected = chartType,
            onSelect = onChartTypeChange,
        )

        SingleChoiceSegmentedSelector(
            options = HomeChartWindow.values().map { window ->
                SegmentedOption(window, stringResource(id = window.labelRes()))
            },
            selected = chartWindow,
            onSelect = onChartWindowChange,
        )

        PieChart(
            slices = when (chartType) {
                HomeChartType.FORWARD -> listOf(
                    PieSlice(
                        label = stringResource(id = R.string.home_event_forward_success),
                        value = data?.forwardSuccess ?: 0L,
                        color = colors[0],
                    ),
                    PieSlice(
                        label = stringResource(id = R.string.home_event_forward_failed),
                        value = data?.forwardFailed ?: 0L,
                        color = colors[3],
                    ),
                )
                HomeChartType.EVENTS -> listOf(
                    PieSlice(
                        label = stringResource(id = R.string.home_event_code_detected),
                        value = data?.codeDetected ?: 0L,
                        color = colors[0],
                    ),
                    PieSlice(
                        label = stringResource(id = R.string.home_event_auto_input_success),
                        value = data?.autoInputSuccess ?: 0L,
                        color = colors[1],
                    ),
                    PieSlice(
                        label = stringResource(id = R.string.home_event_auto_input_failed),
                        value = data?.autoInputFailed ?: 0L,
                        color = colors[3],
                    ),
                    PieSlice(
                        label = stringResource(id = R.string.home_event_forward_success),
                        value = data?.forwardSuccess ?: 0L,
                        color = colors[2],
                    ),
                    PieSlice(
                        label = stringResource(id = R.string.home_event_forward_failed),
                        value = data?.forwardFailed ?: 0L,
                        color = colors[4],
                    ),
                )
                HomeChartType.SENDER -> {
                    if (data == null) {
                        emptyList()
                    } else {
                        data.senderStats.mapIndexed { index, row ->
                            PieSlice(
                                label = getSenderTypeName(context, row.senderType),
                                value = row.sent,
                                color = colors[index % colors.size],
                            )
                        }
                    }
                }
            },
            emptyText = stringResource(id = R.string.home_chart_no_data),
        )
    }
}

@Composable
private fun PieChart(
    slices: List<PieSlice>,
    emptyText: String,
) {
    val total = slices.sumOf { it.value }
    if (total <= 0) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emptyText, color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(140.dp)) {
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = (slice.value.toFloat() / total.toFloat()) * 360f
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                )
                startAngle += sweep
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(slice.color, CircleShape),
                    )
                    Text(text = slice.label, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = slice.value.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value.toString(), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

@Composable
private fun AppInfoCard(
    appVersionName: String,
    appVersionCode: String,
    frameworkType: String,
    frameworkVersion: String,
    hasRootAccess: Boolean,
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val rootHint = stringResource(id = R.string.root_permission_hint)
    val showRootHint: () -> Unit = {
        scope.launch { snackbarHostState.showSnackbar(rootHint) }
        Unit
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            InfoItem(Icons.Default.Star, stringResource(id = R.string.version_name), appVersionName)
            InfoItem(Icons.AutoMirrored.Filled.List, stringResource(id = R.string.version_code), appVersionCode)
            InfoItem(
                Icons.Default.Build,
                stringResource(id = R.string.framework_type),
                frameworkType,
                onClick = if (!interactive || hasRootAccess) {
                    null
                } else {
                    showRootHint
                },
            )
            InfoItem(
                Icons.Default.CheckCircle,
                stringResource(id = R.string.framework_version),
                frameworkVersion,
                onClick = if (!interactive || hasRootAccess) {
                    null
                } else {
                    showRootHint
                },
            )
        }
    }
}

@Composable
private fun DeviceInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            InfoItem(Icons.Default.Build, stringResource(id = R.string.android_version), Build.VERSION.RELEASE)
            InfoItem(Icons.Default.Info, stringResource(id = R.string.android_codename), Build.VERSION.CODENAME)
            InfoItem(Icons.Default.Info, stringResource(id = R.string.api_level), Build.VERSION.SDK_INT.toString())
            InfoItem(Icons.Default.AccountBox, stringResource(id = R.string.manufacturer), Build.MANUFACTURER)
            InfoItem(Icons.Default.Phone, stringResource(id = R.string.model), Build.MODEL)
        }
    }
}

@Composable
private fun LinksCard(
    onCheckUpdate: () -> Unit,
    onDonate: () -> Unit,
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            InfoItem(
                icon = Icons.Default.Info,
                label = stringResource(id = R.string.check_update_title),
                value = stringResource(id = R.string.check_update_summary),
                onClick = if (interactive) onCheckUpdate else null,
            )
            InfoItem(
                icon = Icons.Default.Email,
                label = stringResource(id = R.string.pref_join_qq_group_title),
                value = stringResource(id = R.string.pref_join_qq_group_summary),
                onClick = if (interactive) {
                    { PackageUtils.joinQQGroup(context)?.let { message -> showMessage(message) } }
                } else {
                    null
                },
            )
            InfoItem(
                icon = Icons.AutoMirrored.Filled.Send,
                label = stringResource(id = R.string.pref_join_telegram_group_title),
                value = stringResource(id = R.string.pref_join_telegram_group_summary),
                onClick = if (interactive) {
                    { Utils.showWebPage(context, Const.TELEGRAM_GROUP_URL)?.let { message -> showMessage(message) } }
                } else {
                    null
                },
            )
            InfoItem(
                icon = Icons.Default.Info,
                label = stringResource(id = R.string.pref_source_code_title),
                value = stringResource(id = R.string.pref_source_code_summary),
                onClick = if (interactive) {
                    { Utils.showWebPage(context, Const.PROJECT_SOURCE_CODE_URL)?.let { message -> showMessage(message) } }
                } else {
                    null
                },
            )
            InfoItem(
                icon = Icons.Default.Favorite,
                label = stringResource(id = R.string.pref_donate_by_alipay_title),
                value = stringResource(id = R.string.dialog_donate_summary),
                onClick = if (interactive) onDonate else null,
            )
        }
    }
}

@Composable
fun StatusCard(
    isEnabled: Boolean,
    showDiagnostics: Boolean,
    diagnostics: List<Pair<String, String>>,
    onClick: (() -> Unit)? = null,
) {
    val containerColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        onClick = { onClick?.invoke() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Column {
                    Text(
                        text = if (isEnabled) stringResource(id = R.string.status_working) else stringResource(id = R.string.status_not_active),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!isEnabled) {
                        Text(
                            text = stringResource(id = R.string.status_tip),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (showDiagnostics && diagnostics.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(contentColor.copy(alpha = 0.12f))
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    diagnostics.forEach { (label, value) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.8f),
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildStatusDiagnostics(
    context: android.content.Context,
    snapshot: ActivationDiagnosticsSnapshot,
    runtimeConnected: Boolean,
): List<Pair<String, String>> {
    val serviceValue = buildString {
        append(
            if (runtimeConnected) {
                context.getString(R.string.overview_runtime_connected)
            } else {
                context.getString(R.string.overview_runtime_disconnected)
            },
        )
        if (snapshot.lastServiceBindAtMs > 0L) {
            append(" · ")
            append(context.getString(R.string.overview_runtime_last_connected))
            append(" ")
            append(formatStatusDiagnosticTime(snapshot.lastServiceBindAtMs))
        }
        if (snapshot.lastServiceFrameworkName.isNotBlank() || snapshot.lastServiceFrameworkVersion.isNotBlank()) {
            append(" · ")
            append(snapshot.lastServiceFrameworkName.ifBlank { context.getString(R.string.overview_runtime_unknown) })
            append(" ")
            append(snapshot.lastServiceFrameworkVersion.ifBlank { context.getString(R.string.overview_runtime_unknown) })
        }
    }
    val hookProcess = listOf(
        snapshot.lastHookPackage.ifBlank { context.getString(R.string.overview_runtime_none) },
        snapshot.lastHookProcess.ifBlank { context.getString(R.string.overview_runtime_none) },
    ).joinToString(" / ")
    val hookTime = buildString {
        append(
            if (snapshot.lastHookAtMs > 0L) {
                formatStatusDiagnosticTime(snapshot.lastHookAtMs)
            } else {
                context.getString(R.string.overview_runtime_not_available)
            },
        )
        if (snapshot.lastHookSource.isNotBlank()) {
            append(" · ")
            append(snapshot.lastHookSource)
        }
    }
    return listOf(
        context.getString(R.string.overview_runtime_service_label) to serviceValue,
        context.getString(R.string.overview_runtime_recent_hook_process) to hookProcess,
        context.getString(R.string.overview_runtime_recent_hook_time) to hookTime,
    )
}

private fun formatStatusDiagnosticTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
}

@Composable
private fun FrameworkIncompatibilityCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(id = R.string.framework_incompatibility_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun InfoItem(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
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
