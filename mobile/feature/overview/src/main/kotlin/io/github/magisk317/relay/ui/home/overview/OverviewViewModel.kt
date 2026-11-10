package io.github.magisk317.relay.ui.home.overview

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.relay.common.utils.relayPackageActions
import io.github.magisk317.relay.feature.mode.ShizukuRowState
import io.github.magisk317.relay.feature.mode.StatusCardState
import io.github.magisk317.relay.feature.mode.RuntimeSignalRefresher
import io.github.magisk317.relay.feature.mode.XposedRuntimeState
import io.github.magisk317.relay.feature.mode.NonRootRuntimeState
import io.github.magisk317.relay.feature.mode.overviewRows
import io.github.magisk317.relay.feature.mode.overviewStatusCard
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.settings.OverviewSettingsUpdate
import io.github.magisk317.relay.mobilefeature.overview.BuildConfig
import io.github.magisk317.relay.nonroot.ShizukuGrantBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OverviewViewModel(
    private val settingsRepository: SettingsPreferencesRepository,
    private val runtimeSignals: RuntimeSignalRefresher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverviewUiState())
    internal val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()
    private var pageActive = true
    private var settingsGeneration = 0L
    private var runtimeRefreshGeneration = 0L
    private val runtimeRefreshMutex = Mutex()

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

    /** Called only while the page is RESUMED; cancellation stops observing the runtime flows. */
    internal suspend fun observeRuntimeSnapshot(context: Context) {
        refreshRuntimeSnapshot(context)
        val initial = _uiState.value.runtimeSnapshot
        val shizukuOnline = initial.rows.shizuku != null && initial.rows.shizuku != ShizukuRowState.UNREACHABLE
        val shizukuAuthorized = initial.rows.shizuku == ShizukuRowState.AUTHORIZED
        val shizukuVersion = (initial.statusCard as? StatusCardState.Shizuku)?.version
            ?: if (shizukuAuthorized) ShizukuGrantBridge.shizukuVersion(context) else null
        combine(XposedRuntimeState.xposedActive, NonRootRuntimeState.listenerConnected) { xposed, listener ->
            xposed to listener
        }.distinctUntilChanged().collect { (xposed, listener) ->
            runtimeRefreshMutex.withLock {
                if (!pageActive) return@withLock
                // Binding/listener callbacks already refreshed these signals. Re-read only their
                // display values here; full Shizuku, root and version probes happen once per resume.
                val rows = overviewRows(
                    BuildConfig.ENABLE_NONROOT_MODE, xposed, listener, shizukuOnline, shizukuAuthorized,
                )
                val statusCard = overviewStatusCard(
                    xposedActive = xposed,
                    framework = runtimeSignals.frameworkIdentity(),
                    nonRootBuild = BuildConfig.ENABLE_NONROOT_MODE,
                    shizukuOnline = shizukuOnline,
                    shizukuAuthorized = shizukuAuthorized,
                    shizukuVersion = shizukuVersion,
                )
                _uiState.update { state ->
                    state.copy(runtimeSnapshot = state.runtimeSnapshot.copy(rows = rows, statusCard = statusCard))
                }
            }
        }
    }

    internal suspend fun refreshRuntimeSnapshot(context: Context) {
        if (!pageActive) return
        runtimeRefreshMutex.withLock {
            if (!pageActive) return@withLock
            val generation = ++runtimeRefreshGeneration
            val runtimeSnapshot = loadOverviewRuntimeUiState(context = context, runtimeSignals = runtimeSignals)
            currentCoroutineContext().ensureActive()
            if (!pageActive || generation != runtimeRefreshGeneration) return@withLock
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
}

private suspend fun loadOverviewRuntimeUiState(
    context: Context,
    runtimeSignals: RuntimeSignalRefresher,
): OverviewRuntimeUiState = withContext(Dispatchers.IO) {
    runtimeSignals.refresh()
    coroutineScope {
        val hasRootAccessDeferred = async { relayPackageActions.hasRootAccess() }
        val appVersionNameDeferred = async {
            relayPackageActions.getPackageVersion(context, context.packageName)?.first
        }
        val xposedActive = XposedRuntimeState.xposedActive.value
        val listenerConnected = NonRootRuntimeState.listenerConnected.value
        val shizukuOnline = BuildConfig.ENABLE_NONROOT_MODE && ShizukuGrantBridge.isSupported()
        val shizukuAuthorized = shizukuOnline && ShizukuGrantBridge.isAuthorized()
        OverviewRuntimeUiState(
            rows = overviewRows(
                BuildConfig.ENABLE_NONROOT_MODE, xposedActive, listenerConnected,
                shizukuOnline, shizukuAuthorized,
            ),
            statusCard = overviewStatusCard(
                xposedActive = xposedActive,
                framework = runtimeSignals.frameworkIdentity(),
                nonRootBuild = BuildConfig.ENABLE_NONROOT_MODE,
                shizukuOnline = shizukuOnline,
                shizukuAuthorized = shizukuAuthorized,
                shizukuVersion = if (shizukuAuthorized) ShizukuGrantBridge.shizukuVersion(context) else null,
            ),
            hasRootAccess = hasRootAccessDeferred.await(),
            appVersionName = appVersionNameDeferred.await()?.takeIf { it.isNotBlank() }
                ?: context.getString(io.github.magisk317.relay.mobile.core.R.string.unknown),
            commitInfo = io.github.magisk317.relay.mobile.core.BuildConfig.COMMIT_HASH
                .takeIf { it.isNotBlank() && it != "unknown" }
                ?: context.getString(io.github.magisk317.relay.mobile.core.R.string.unknown),
        )
    }
}
