package io.github.magisk317.relay.ui.home.overview

import io.github.magisk317.relay.android.otel.MagiskOtelBootstrap
import io.github.magisk317.relay.mobilefeature.overview.BuildConfig
import io.github.magisk317.relay.ui.common.rememberPrefBoolean
import io.github.magisk317.uikit.surface.DonateDialog
import io.github.magisk317.uikit.surface.QRCodeDialog
import io.github.magisk317.uikit.surface.saveImageToGalleryAsync
import io.github.magisk317.uikit.R as UiKitR

import io.github.magisk317.uikit.common.showLatestSnackbar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import io.github.magisk317.relay.feature.mode.BatteryOptimizationHelper
import io.github.magisk317.relay.feature.mode.StandardModePermissions
import io.github.magisk317.relay.feature.mode.WorkMode
import io.github.magisk317.relay.feature.mode.WorkModeResolver
import io.github.magisk317.relay.ui.common.LocalSnackbarHostState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.contract.constant.RelayPrefConst as PrefConst
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.smscode.runtime.common.utils.BrowserUtils
import io.github.magisk317.relay.core.R
import io.github.magisk317.uikit.surface.chromeTopAppBarColors
import io.github.magisk317.relay.engine.service.RuntimeAnalyticsProvider
import io.github.magisk317.relay.billing.BillingProvider
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

internal data class HomeCardSpec(
    val id: String,
    val titleRes: Int,
    val icon: ImageVector,
    val enabledByDefault: Boolean = true,
    val available: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(onCheckUpdate: () -> Unit) {
    val context = LocalContext.current
    val viewModel: OverviewViewModel = koinViewModel()
    val analyticsRepository: RuntimeAnalyticsProvider = koinInject()
    val billingProvider: BillingProvider = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    fun showMessage(message: String) {
        coroutineScope.launch { snackbarHostState.showLatestSnackbar(message) }
    }
    var showDonateDialog by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }

    val analyticsEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE_ANALYTICS, true)
    val effectiveAnalyticsEnabled = MagiskOtelBootstrap.isEffectivelyEnabled(analyticsEnabled.value)

    val workMode by WorkModeResolver.mode.collectAsStateWithLifecycle()
    val isEnabled = workMode == WorkMode.Enhanced
    val isStandardEnabled = workMode == WorkMode.Standard

    fun refreshBatteryOptimizationExemption(): Boolean {
        val exempted = !isStandardEnabled || BatteryOptimizationHelper.isExempted(context)
        viewModel.updateBatteryOptimizationExempted(exempted)
        return exempted
    }

    LaunchedEffect(isStandardEnabled) {
        if (isStandardEnabled) {
            // Standard mode remains active while individual capabilities request their permissions.
            val missing = StandardModePermissions.missingPermissions(context)
            if (missing.isNotEmpty()) {
                showMessage(context.getString(R.string.standard_mode_missing_permissions_hint))
                val activity = context.findActivity()
                activity?.requestPermissions(
                    missing.toTypedArray(),
                    Const.REQUEST_CODE_STANDARD_PERMISSIONS
                )
            }
        }
    }

    // Prompt battery optimization exemption for Standard mode
    LaunchedEffect(isStandardEnabled) {
        if (!refreshBatteryOptimizationExemption()) {
            showMessage(context.getString(R.string.standard_mode_battery_optimization_hint))
        }
    }

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val density = LocalDensity.current
    val dragThresholdPx = remember(density) { with(density) { 72.dp.toPx() } }
    val overviewUiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OverviewEvent.StatusDiagnosticsVisibilityChanged -> {
                    showMessage(
                        if (event.visible) {
                            context.getString(R.string.overview_runtime_diagnostics_shown)
                        } else {
                            context.getString(R.string.overview_runtime_diagnostics_hidden)
                        },
                    )
                }
            }
        }
    }

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
            available = effectiveAnalyticsEnabled,
        ),
    )
    val cardSpecById = remember(allCardSpecs) { allCardSpecs.associateBy(HomeCardSpec::id) }

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }
    LaunchedEffect(effectiveAnalyticsEnabled, overviewUiState.chartWindow) {
        viewModel.refreshRuntimeSnapshot(
            context = context,
            analyticsRepository = analyticsRepository,
            analyticsEnabled = effectiveAnalyticsEnabled,
        )
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshBatteryOptimizationExemption()
        coroutineScope.launch {
            viewModel.refreshRuntimeSnapshot(
                context = context,
                analyticsRepository = analyticsRepository,
                analyticsEnabled = effectiveAnalyticsEnabled,
            )
        }
    }
    val cardOrder = overviewUiState.cardOrder
    val enabledCardIds = overviewUiState.enabledCardIds
    val chartType = overviewUiState.chartType
    val chartWindow = overviewUiState.chartWindow
    val runtimeUiState = overviewUiState.runtimeSnapshot

    val visibleCardIds = remember(cardOrder, enabledCardIds, effectiveAnalyticsEnabled) {
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

    OverviewContent(
        listState = listState,
        scrollBehavior = scrollBehavior,
        visibleCardSpecs = visibleCardSpecs,
        cardOrder = cardOrder,
        enabledCardIds = enabledCardIds,
        editMode = overviewUiState.editMode,
        isEnabled = isEnabled,
        isStandardEnabled = isStandardEnabled,
        showBatteryOptimizationHint = isStandardEnabled && !overviewUiState.batteryOptimizationExempted,
        chartType = chartType,
        chartWindow = chartWindow,
        chartSnapshot = runtimeUiState.chartSnapshot,
        appVersionName = runtimeUiState.appVersionName,
        appVersionCode = runtimeUiState.appVersionCode,
        frameworkType = runtimeUiState.frameworkType,
        frameworkVersion = runtimeUiState.frameworkVersion,
        hasRootAccess = runtimeUiState.hasRootAccess,
        runtimeConnected = runtimeUiState.runtimeConnected,
        activationDiagnostics = runtimeUiState.activationDiagnostics,
        showStatusDiagnostics = overviewUiState.showStatusDiagnostics,
        draggingCardId = overviewUiState.draggingCardId,
        dragOffsetY = overviewUiState.dragOffsetY,
        dragThresholdPx = dragThresholdPx,
        showAddAction = overviewUiState.editMode && addableCardSpecs.isNotEmpty(),
        onToggleEditMode = viewModel::toggleEditMode,
        onShowAddSheet = { viewModel.setAddCardSheetVisible(true) },
        onRequestEnableEditMode = {},
        onCardOrderChange = viewModel::updateCardOrderLocally,
        onPersistCardOrder = viewModel::persistCardOrder,
        onEnabledCardIdsChange = viewModel::updateEnabledCardIdsLocally,
        onPersistEnabledCardIds = viewModel::persistEnabledCardIds,
        onDragStateChange = viewModel::updateDragState,
        onChartTypeChange = { next ->
            viewModel.setChartType(next)
        },
        onChartWindowChange = { next ->
            viewModel.setChartWindow(next)
        },
        onCheckUpdate = onCheckUpdate,
        onShowDonate = { showDonateDialog = true },
        onBatteryOptimizationClick = {
            runCatching {
                BatteryOptimizationHelper.requestExemption(context)
            }.onFailure { error ->
                showMessage(error.message ?: context.getString(R.string.standard_mode_battery_optimization_hint))
            }
        },
        onStatusCardTap = { viewModel.onStatusCardTapped(android.os.SystemClock.uptimeMillis()) },
    )

    OverviewDialogs(
        context = context,
        showAddCardSheet = overviewUiState.showAddCardSheet,
        addableCardSpecs = addableCardSpecs,
        showDonateDialog = showDonateDialog,
        showQRCodeDialog = showQRCodeDialog,
        onDismissAddSheet = { viewModel.setAddCardSheetVisible(false) },
        onAddCard = viewModel::addCard,
        onToggleDonateDialog = { showDonateDialog = it },
        onShowQrCodeDialog = { showQRCodeDialog = it },
        onPlayDonation = { productId ->
            context.findActivity()?.let { activity ->
                billingProvider.launchDonation(activity, productId)
            }
        },
        onShowMessage = ::showMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewContent(
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    visibleCardSpecs: List<HomeCardSpec>,
    cardOrder: List<String>,
    enabledCardIds: Set<String>,
    editMode: Boolean,
    isEnabled: Boolean,
    isStandardEnabled: Boolean,
    showBatteryOptimizationHint: Boolean,
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
    onBatteryOptimizationClick: () -> Unit,
    onStatusCardTap: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showLatestSnackbar(message) }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
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
                    isStandardEnabled = isStandardEnabled,
                    showBatteryOptimizationHint = showBatteryOptimizationHint,
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
                    onBatteryOptimizationClick = onBatteryOptimizationClick,
                    onStatusCardTap = onStatusCardTap,
                )
            }
        }

        TopAppBar(
            title = { Text(text = stringResource(id = R.string.app_name)) },
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets.statusBars,
            modifier = Modifier
                .align(Alignment.TopCenter),
            colors = chromeTopAppBarColors(),
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
    isStandardEnabled: Boolean,
    showBatteryOptimizationHint: Boolean,
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
    onBatteryOptimizationClick: () -> Unit,
    onStatusCardTap: () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val snackbarHostState = io.github.magisk317.relay.ui.common.LocalSnackbarHostState.current
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
                    isEnhancedModeEnabled = isEnabled,
                    isStandardModeEnabled = isStandardEnabled,
                    showBatteryOptimizationHint = showBatteryOptimizationHint,
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
                    onBatteryOptimizationClick = if (editMode) {
                        null
                    } else {
                        onBatteryOptimizationClick
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
                io.github.magisk317.uikit.surface.OverviewAppInfoCard(
                    appVersionName = appVersionName,
                    appVersionCode = appVersionCode,
                    frameworkType = frameworkType,
                    frameworkVersion = frameworkVersion,
                    interactive = !editMode,
                    onRootHint = if (hasRootAccess) {
                        null
                    } else {
                        {
                            scope.launch {
                                snackbarHostState.showLatestSnackbar(context.getString(R.string.root_permission_hint))
                            }
                        }
                    },
                )
            }
            CARD_DEVICE_INFO -> io.github.magisk317.uikit.surface.OverviewDeviceInfoCard()
            CARD_LINKS -> {
                io.github.magisk317.uikit.surface.OverviewLinksCard(
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
                    onJoinQQ = {
                        io.github.magisk317.relay.common.utils.PackageUtils.joinQQGroup(context)?.let {
                            scope.launch { snackbarHostState.showLatestSnackbar(it) }
                        }
                    },
                    onJoinTelegram = {
                        BrowserUtils.openWebPage(
                            context,
                            Const.TELEGRAM_GROUP_URL,
                            R.string.browser_install_or_enable_prompt,
                        )?.let {
                            scope.launch { snackbarHostState.showLatestSnackbar(it) }
                        }
                    },
                    onSourceCode = {
                        BrowserUtils.openWebPage(
                            context,
                            Const.PROJECT_SOURCE_CODE_URL,
                            R.string.browser_install_or_enable_prompt,
                        )?.let {
                            scope.launch { snackbarHostState.showLatestSnackbar(it) }
                        }
                    },
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
    onPlayDonation: (String) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
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
                onShowQrCodeDialog(Pair(UiKitR.drawable.alipay, "alipay"))
            },
            onWechat = {
                onToggleDonateDialog(false)
                onShowQrCodeDialog(Pair(UiKitR.drawable.wx, "wechat"))
            },
            showPlayDonations = BuildConfig.HAS_BILLING,
            onDonate099 = { onPlayDonation("donate_099") },
            onDonate200 = { onPlayDonation("donate_200") },
            onDonate999 = { onPlayDonation("donate_999") },
            onDonate1999 = { onPlayDonation("donate_1999") },
        )
    }

    showQRCodeDialog?.let { pair ->
        QRCodeDialog(
            resId = pair.first,
            type = pair.second,
            onDismiss = { onShowQrCodeDialog(null) },
            onSave = {
                scope.launch {
                    saveImageToGalleryAsync(context, pair.first, "${pair.second}_qrcode")
                        .forEach(onShowMessage)
                }
            },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
