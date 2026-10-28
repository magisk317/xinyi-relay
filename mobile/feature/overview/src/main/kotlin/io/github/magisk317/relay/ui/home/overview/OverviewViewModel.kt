package io.github.magisk317.relay.ui.home.overview

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.common.utils.relayPackageActions
import io.github.magisk317.relay.contract.constant.RelayAppConst as Const
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.settings.OverviewSettingsUpdate
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OverviewViewModel(
    private val settingsRepository: SettingsPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverviewUiState())
    internal val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<OverviewEvent>(extraBufferCapacity = 4)
    internal val events: SharedFlow<OverviewEvent> = _events.asSharedFlow()
    private var pageActive = true
    private var settingsGeneration = 0L
    private var runtimeRefreshGeneration = 0L
    private val runtimeRefreshMutex = Mutex()
    private var runtimeSnapshotLoaded = false

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

        if (!pageActive || generation != settingsGeneration) return
        _uiState.update { current ->
            current.copy(
                cardOrder = normalizedOrder,
                enabledCardIds = normalizedEnabled,
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
    }

    internal suspend fun refreshRuntimeSnapshot(context: Context) {
        if (!pageActive) return
        if (runtimeSnapshotLoaded) return
        runtimeRefreshMutex.withLock {
            if (!pageActive || runtimeSnapshotLoaded) return@withLock
            val generation = ++runtimeRefreshGeneration
            val runtimeSnapshot = loadOverviewRuntimeUiState(context = context)
            currentCoroutineContext().ensureActive()
            if (!pageActive || generation != runtimeRefreshGeneration) return@withLock
            runtimeSnapshotLoaded = true
            _uiState.update { it.copy(runtimeSnapshot = runtimeSnapshot) }
        }
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

    internal fun toggleStatusDiagnostics() {
        val nextVisible = !_uiState.value.showStatusDiagnostics
        _uiState.update { it.copy(showStatusDiagnostics = nextVisible) }
        _events.tryEmit(OverviewEvent.StatusDiagnosticsVisibilityChanged(nextVisible))
    }
}

private suspend fun loadOverviewRuntimeUiState(
    context: Context,
): OverviewRuntimeUiState = withContext(Dispatchers.IO) {
    coroutineScope {
        val frameworkInfoDeferred = async { relayPackageActions.getLsposedModuleInfo(context) }
        val hasRootAccessDeferred = async { relayPackageActions.hasRootAccess() }
        val appVersionDeferred = async { relayPackageActions.getPackageVersion(context, context.packageName) }

        val frameworkInfo = frameworkInfoDeferred.await()
        val frameworkType = frameworkInfo?.first ?: context.getString(io.github.magisk317.relay.mobile.core.R.string.unknown)
        val frameworkVersion = frameworkInfo?.second ?: run {
            val lsposedVersion = relayPackageActions.getPackageVersion(context, Const.LSPOSED_MANAGER_PACKAGE_NAME)
            when {
                lsposedVersion != null && lsposedVersion.first.isNotBlank() ->
                    "${lsposedVersion.first} (${lsposedVersion.second})"

                relayPackageActions.isPackageInstalled(context, Const.LSPOSED_MANAGER_PACKAGE_NAME) ->
                    context.getString(io.github.magisk317.relay.mobile.core.R.string.unknown)

                else -> context.getString(io.github.magisk317.relay.mobile.core.R.string.not_installed)
            }
        }
        val appVersion = appVersionDeferred.await()
        OverviewRuntimeUiState(
            frameworkType = frameworkType,
            frameworkVersion = frameworkVersion,
            hasRootAccess = hasRootAccessDeferred.await(),
            appVersionName = appVersion?.first?.takeIf { it.isNotBlank() }
                ?: context.getString(io.github.magisk317.relay.mobile.core.R.string.unknown),
            appVersionCode = io.github.magisk317.relay.mobile.core.BuildConfig.COMMIT_HASH.takeIf { it.isNotBlank() && it != "unknown" }
                ?: (appVersion?.second?.toString()
                ?: context.getString(io.github.magisk317.relay.mobile.core.R.string.unknown)),
        )
    }
}
