package io.github.magisk317.relay.ui.home.overview

import io.github.magisk317.relay.contract.model.ForwardCommonConfig
import io.github.magisk317.relay.contract.repository.SettingsPreferencesRepository
import io.github.magisk317.relay.contract.settings.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class OverviewViewModelTest {

    @Test
    fun loadSettings_normalizesInvalidStoredValues() = runBlocking {
        val repository = FakeOverviewSettingsRepository(
            overviewSettings = OverviewSettingsSnapshot(
                cardOrder = "chart,status,chart,unknown",
                enabledCardIds = "unknown",
                chartType = "unknown",
                chartWindow = "unknown",
            ),
        )
        val viewModel = OverviewViewModel(repository)

        viewModel.loadSettings()

        val state = viewModel.uiState.value
        assertEquals(listOf(CARD_CHART, CARD_STATUS, CARD_APP_INFO, CARD_DEVICE_INFO, CARD_LINKS), state.cardOrder)
        assertEquals(DEFAULT_OVERVIEW_ENABLED_CARD_IDS, state.enabledCardIds)
        assertEquals(HomeChartType.EVENTS, state.chartType)
        assertEquals(HomeChartWindow.ALL, state.chartWindow)
    }

    @Test
    fun localStateUpdates_changeCardOrderAndEnabledSet() = runBlocking {
        val repository = FakeOverviewSettingsRepository(
            overviewSettings = OverviewSettingsSnapshot(
                cardOrder = "status,app_info",
                enabledCardIds = "status,app_info",
                chartType = HomeChartType.EVENTS.id,
                chartWindow = HomeChartWindow.ALL.id,
            ),
        )
        val viewModel = OverviewViewModel(repository)
        viewModel.loadSettings()

        viewModel.updateEnabledCardIdsLocally(setOf(CARD_STATUS, CARD_CHART))
        viewModel.updateCardOrderLocally(listOf(CARD_CHART, CARD_STATUS))

        val state = viewModel.uiState.value
        assertEquals(true, CARD_CHART in state.enabledCardIds)
        assertEquals(listOf(CARD_CHART, CARD_STATUS), state.cardOrder)
    }

    @Test
    fun toggleEditMode_andDragState_resetsShellEditingStateWhenExiting() = runBlocking {
        val viewModel = OverviewViewModel(
            FakeOverviewSettingsRepository(
                overviewSettings = OverviewSettingsSnapshot(
                    encodeCardList(DEFAULT_OVERVIEW_CARD_ORDER),
                    encodeCardList(DEFAULT_OVERVIEW_ENABLED_CARD_IDS.toList()),
                    HomeChartType.EVENTS.id,
                    HomeChartWindow.ALL.id,
                ),
            ),
        )

        viewModel.toggleEditMode()
        viewModel.setAddCardSheetVisible(true)
        viewModel.updateDragState(CARD_STATUS, 48f)
        viewModel.toggleEditMode()

        val state = viewModel.uiState.value
        assertFalse(state.editMode)
        assertFalse(state.showAddCardSheet)
        assertEquals(null, state.draggingCardId)
        assertEquals(0f, state.dragOffsetY)
    }

    @Test
    fun onStatusCardTapped_togglesDiagnosticsAfterFiveTaps_andEmitsEvent() = runBlocking {
        val viewModel = OverviewViewModel(
            FakeOverviewSettingsRepository(
                overviewSettings = OverviewSettingsSnapshot(
                    encodeCardList(DEFAULT_OVERVIEW_CARD_ORDER),
                    encodeCardList(DEFAULT_OVERVIEW_ENABLED_CARD_IDS.toList()),
                    HomeChartType.EVENTS.id,
                    HomeChartWindow.ALL.id,
                ),
            ),
        )
        val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }

        repeat(5) { index ->
            viewModel.onStatusCardTapped(1000L + index * 100L)
        }

        assertTrue(viewModel.uiState.value.showStatusDiagnostics)
        assertEquals(
            OverviewEvent.StatusDiagnosticsVisibilityChanged(true),
            eventDeferred.await(),
        )
    }
}

private class FakeOverviewSettingsRepository(
    private var overviewSettings: OverviewSettingsSnapshot,
) : SettingsPreferencesRepository {
    override suspend fun getOverviewSettings(): OverviewSettingsSnapshot = overviewSettings

    override suspend fun updateOverviewSettings(update: OverviewSettingsUpdate): OverviewSettingsSnapshot {
        overviewSettings = overviewSettings.copy(
            cardOrder = update.cardOrder ?: overviewSettings.cardOrder,
            enabledCardIds = update.enabledCardIds ?: overviewSettings.enabledCardIds,
            chartType = update.chartType ?: overviewSettings.chartType,
            chartWindow = update.chartWindow ?: overviewSettings.chartWindow,
        )
        return overviewSettings
    }

    override suspend fun getGeneralSettings(): GeneralSettingsSnapshot = unsupported()
    override suspend fun updateGeneralSettings(update: GeneralSettingsUpdate): GeneralSettingsSnapshot = unsupported()
    override suspend fun getVerificationSettings(): VerificationSettingsSnapshot = unsupported()
    override suspend fun updateVerificationSettings(update: VerificationSettingsUpdate): VerificationSettingsSnapshot = unsupported()
    override suspend fun getRelaySettings(): RelaySettingsSnapshot = unsupported()
    override suspend fun updateRelaySettings(update: RelaySettingsUpdate): RelaySettingsSnapshot = unsupported()
    override suspend fun getDiagnosticsSettings(): DiagnosticsSettingsSnapshot = unsupported()
    override suspend fun updateDiagnosticsSettings(update: DiagnosticsSettingsUpdate): DiagnosticsSettingsSnapshot = unsupported()
    override suspend fun getAdvancedSnapshot(): AdvancedSettingsSnapshot = unsupported()
    override suspend fun updateAdvanced(update: AdvancedSettingsUpdate): AdvancedSettingsSnapshot = unsupported()
    override suspend fun getSpecialAlertSettings(): SpecialAlertSettingsSnapshot = unsupported()
    override suspend fun updateSpecialAlertSettings(update: SpecialAlertSettingsUpdate): SpecialAlertSettingsSnapshot = unsupported()
    override suspend fun getMessageTypeGates(): MessageTypeGateSnapshot = unsupported()
    override suspend fun updateMessageTypeGates(update: MessageTypeGateUpdate): MessageTypeGateSnapshot = unsupported()
    override suspend fun getForwardTypeGates(): ForwardTypeGateSnapshot = unsupported()
    override suspend fun updateForwardTypeGates(update: ForwardTypeGateUpdate): ForwardTypeGateSnapshot = unsupported()
    override suspend fun getRecordSettings(): RecordSettingsSnapshot = unsupported()
    override suspend fun updateRecordSettings(update: RecordSettingsUpdate): RecordSettingsSnapshot = unsupported()
    override suspend fun getSmsBlacklistSettings(): SmsBlacklistSettingsSnapshot = unsupported()
    override suspend fun updateSmsBlacklistSettings(update: SmsBlacklistSettingsUpdate): SmsBlacklistSettingsSnapshot = unsupported()
    override suspend fun getSimRemarkSettings(): SimRemarkSettingsSnapshot = unsupported()
    override suspend fun updateSimRemarkSettings(update: SimRemarkSettingsUpdate): SimRemarkSettingsSnapshot = unsupported()
    override suspend fun loadForwardCommonConfig(): ForwardCommonConfig = unsupported()
    override suspend fun saveForwardCommonConfig(config: ForwardCommonConfig) = unsupported<Unit>()
    override suspend fun loadAppNotifyTemplate(): String = unsupported()
    override suspend fun saveAppNotifyTemplate(template: String) = unsupported<Unit>()
    override suspend fun loadCallNotifyTemplate(): String = unsupported()
    override suspend fun saveCallNotifyTemplate(template: String) = unsupported<Unit>()
    override suspend fun getUserSettingsSnapshot(): UserSettingsSnapshot = unsupported()
    override suspend fun updateUserSettings(update: UserSettingsUpdate): UserSettingsSnapshot = unsupported()
    override suspend fun getAutoUpdateSettings(): AutoUpdateSettingsSnapshot = unsupported()
    override suspend fun setIgnoredGithubVersion(versionName: String) = unsupported<Unit>()
    override suspend fun getThemeMode(): Int = unsupported()
    override suspend fun setThemeMode(mode: Int) = unsupported<Unit>()
    override suspend fun getUiKitStyle(): Int = unsupported()
    override suspend fun setUiKitStyle(style: Int) = unsupported<Unit>()
    override suspend fun getLanguageTag(): String = unsupported()
    override suspend fun setLanguageTag(languageTag: String) = unsupported<Unit>()
    override suspend fun isPrivacyPolicyAccepted(): Boolean = unsupported()
    override suspend fun setPrivacyPolicyAccepted(accepted: Boolean) = unsupported<Unit>()
    override suspend fun clearBatteryReminderRuntimeFlags(
        clearLowBatteryBelow: Boolean,
        clearFullBatteryAbove: Boolean,
        clearChargingState: Boolean,
    ) = unsupported<Unit>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> unsupported(): T = throw UnsupportedOperationException()
}
