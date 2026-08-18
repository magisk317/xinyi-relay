package io.github.magisk317.relay.ui.home.overview

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.relay.common.utils.PackageUtils
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.settings.OverviewSettingsUpdate
import io.github.magisk317.relay.engine.service.RuntimeAnalyticsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverviewViewModel(
    private val settingsRepository: SettingsPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverviewUiState())
    internal val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<OverviewEvent>(extraBufferCapacity = 4)
    internal val events: SharedFlow<OverviewEvent> = _events.asSharedFlow()
    private var statusTapCount = 0
    private var statusTapStartedAtMs = 0L
    private var pageActive = true
    private var settingsGeneration = 0L
    private var runtimeRefreshGeneration = 0L

    internal fun setPageActive(active: Boolean) {
        if (pageActive == active) return
        pageActive = active
        if (!active) {
            settingsGeneration++
            runtimeRefreshGeneration++
        }
    }

    internal fun invalidateRuntimeRefresh() {
        runtimeRefreshGeneration++
    }

    internal suspend fun loadSettings() {
        if (!pageActive) return
        val generation = ++settingsGeneration
        val overviewSettings = settingsRepository.getOverviewSettings()
        val storedOrder = overviewSettings.cardOrder
        val parsedOrder = if (storedOrder.isBlank()) DEFAULT_OVERVIEW_CARD_ORDER else parseCardList(storedOrder)
        val normalizedOrder = normalizeCardOrder(parsedOrder, DEFAULT_OVERVIEW_CARD_ORDER)

        val storedEnabled = overviewSettings.enabledCardIds
        val parsedEnabled = if (storedEnabled.isBlank()) {
            DEFAULT_OVERVIEW_ENABLED_CARD_IDS
        } else {
            parseCardList(storedEnabled).toSet()
        }
        val normalizedEnabled = normalizeCardEnabled(parsedEnabled, DEFAULT_OVERVIEW_ENABLED_CARD_IDS)

        val storedChartType = overviewSettings.chartType
        val parsedChartType = HomeChartType.fromId(storedChartType) ?: HomeChartType.EVENTS

        val storedChartWindow = overviewSettings.chartWindow
        val parsedChartWindow = HomeChartWindow.fromId(storedChartWindow) ?: HomeChartWindow.ALL

        if (!pageActive || generation != settingsGeneration) return
        _uiState.update { current ->
            current.copy(
                cardOrder = normalizedOrder,
                enabledCardIds = normalizedEnabled,
                chartType = parsedChartType,
                chartWindow = parsedChartWindow,
            )
        }

        if (normalizedOrder != parsedOrder || storedOrder.isBlank()) {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(cardOrder = encodeCardList(normalizedOrder)),
            )
        }
        if (normalizedEnabled != parsedEnabled || storedEnabled.isBlank()) {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(enabledCardIds = encodeCardList(normalizedEnabled.toList())),
            )
        }
        if (storedChartType.isBlank() || parsedChartType.id != storedChartType) {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(chartType = parsedChartType.id),
            )
        }
        if (storedChartWindow.isBlank() || parsedChartWindow.id != storedChartWindow) {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(chartWindow = parsedChartWindow.id),
            )
        }
    }

    internal suspend fun refreshRuntimeSnapshot(
        context: Context,
        analyticsRepository: RuntimeAnalyticsProvider,
        analyticsEnabled: Boolean,
    ) {
        if (!pageActive) return
        val generation = ++runtimeRefreshGeneration
        val chartWindow = _uiState.value.chartWindow
        val runtimeSnapshot = loadOverviewRuntimeUiState(
            context = context,
            analyticsRepository = analyticsRepository,
            chartWindow = chartWindow,
            analyticsEnabled = analyticsEnabled,
        )
        currentCoroutineContext().ensureActive()
        if (!pageActive || generation != runtimeRefreshGeneration) return
        _uiState.update { it.copy(runtimeSnapshot = runtimeSnapshot) }
    }

    internal fun updateCardOrderLocally(newOrder: List<String>) {
        _uiState.update { it.copy(cardOrder = newOrder) }
    }

    internal fun persistCardOrder(newOrder: List<String>) {
        viewModelScope.launch {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(cardOrder = encodeCardList(newOrder)),
            )
        }
    }

    internal fun updateEnabledCardIdsLocally(newEnabled: Set<String>) {
        _uiState.update { it.copy(enabledCardIds = newEnabled) }
    }

    internal fun persistEnabledCardIds(newEnabled: Set<String>) {
        viewModelScope.launch {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(enabledCardIds = encodeCardList(newEnabled.toList())),
            )
        }
    }

    internal fun addCard(cardId: String) {
        val current = _uiState.value
        val newEnabled = current.enabledCardIds + cardId
        val nextOrder = if (current.cardOrder.contains(cardId)) {
            current.cardOrder
        } else {
            normalizeCardOrder(current.cardOrder + cardId, DEFAULT_OVERVIEW_CARD_ORDER)
        }
        _uiState.value = current.copy(
            enabledCardIds = newEnabled,
            cardOrder = nextOrder,
            showAddCardSheet = false,
        )
        viewModelScope.launch {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(
                    enabledCardIds = encodeCardList(newEnabled.toList()),
                    cardOrder = encodeCardList(nextOrder),
                ),
            )
        }
    }

    internal fun setChartType(next: HomeChartType) {
        _uiState.update { it.copy(chartType = next) }
        viewModelScope.launch {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(chartType = next.id),
            )
        }
    }

    internal fun setChartWindow(next: HomeChartWindow) {
        _uiState.update { it.copy(chartWindow = next) }
        viewModelScope.launch {
            settingsRepository.updateOverviewSettings(
                OverviewSettingsUpdate(chartWindow = next.id),
            )
        }
    }

    internal fun toggleEditMode() {
        _uiState.update { current ->
            if (current.editMode) {
                current.copy(
                    editMode = false,
                    showAddCardSheet = false,
                    draggingCardId = null,
                    dragOffsetY = 0f,
                )
            } else {
                current.copy(editMode = true)
            }
        }
    }

    internal fun setAddCardSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showAddCardSheet = visible) }
    }

    internal fun updateDragState(cardId: String?, offsetY: Float) {
        _uiState.update { it.copy(draggingCardId = cardId, dragOffsetY = offsetY) }
    }

    internal fun updateBatteryOptimizationExempted(exempted: Boolean) {
        _uiState.update { it.copy(batteryOptimizationExempted = exempted) }
    }

    internal fun onStatusCardTapped(nowMs: Long) {
        val withinWindow = nowMs - statusTapStartedAtMs <= 1800L
        statusTapCount = if (withinWindow) statusTapCount + 1 else 1
        statusTapStartedAtMs = nowMs
        if (statusTapCount < 5) {
            return
        }
        val nextVisible = !_uiState.value.showStatusDiagnostics
        _uiState.update { it.copy(showStatusDiagnostics = nextVisible) }
        statusTapCount = 0
        statusTapStartedAtMs = 0L
        _events.tryEmit(OverviewEvent.StatusDiagnosticsVisibilityChanged(nextVisible))
    }
}

private suspend fun loadOverviewRuntimeUiState(
    context: Context,
    analyticsRepository: RuntimeAnalyticsProvider,
    chartWindow: HomeChartWindow,
    analyticsEnabled: Boolean,
): OverviewRuntimeUiState = withContext(Dispatchers.IO) {
    coroutineScope {
        val frameworkInfoDeferred = async { PackageUtils.getLsposedModuleInfo(context) }
        val hasRootAccessDeferred = async { PackageUtils.hasRootAccess() }
        val appVersionDeferred = async { PackageUtils.getPackageVersion(context, context.packageName) }
        val chartSnapshotDeferred = async {
            if (!analyticsEnabled) {
                null
            } else {
                val nowMs = System.currentTimeMillis()
                val fromMs = chartWindow.fromMs(nowMs)
                val snapshot = analyticsRepository.snapshot(fromMs)
                HomeAnalyticsSnapshot(
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
        }

        val frameworkInfo = frameworkInfoDeferred.await()
        val frameworkType = frameworkInfo?.first ?: context.getString(io.github.magisk317.relay.core.R.string.unknown)
        val frameworkVersion = frameworkInfo?.second ?: run {
            val lsposedVersion = PackageUtils.getPackageVersion(context, Const.LSPOSED_MANAGER_PACKAGE_NAME)
            when {
                lsposedVersion != null && lsposedVersion.first.isNotBlank() ->
                    "${lsposedVersion.first} (${lsposedVersion.second})"

                PackageUtils.isPackageInstalled(context, Const.LSPOSED_MANAGER_PACKAGE_NAME) ->
                    context.getString(io.github.magisk317.relay.core.R.string.unknown)

                else -> context.getString(io.github.magisk317.relay.core.R.string.not_installed)
            }
        }
        val appVersion = appVersionDeferred.await()
        OverviewRuntimeUiState(
            runtimeConnected = ActivationDiagnosticsStore.isRuntimeConnected(),
            activationDiagnostics = ActivationDiagnosticsStore.snapshot(context),
            frameworkType = frameworkType,
            frameworkVersion = frameworkVersion,
            hasRootAccess = hasRootAccessDeferred.await(),
            appVersionName = appVersion?.first?.takeIf { it.isNotBlank() }
                ?: context.getString(io.github.magisk317.relay.core.R.string.unknown),
            appVersionCode = appVersion?.second?.toString()
                ?: context.getString(io.github.magisk317.relay.core.R.string.unknown),
            chartSnapshot = chartSnapshotDeferred.await(),
        )
    }
}
