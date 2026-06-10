package io.github.magisk317.relay.contract.repository

import io.github.magisk317.relay.contract.model.ForwardCommonConfig
import io.github.magisk317.relay.contract.settings.*
import kotlinx.coroutines.flow.Flow

interface SettingsPreferencesRepository {
    // General
    suspend fun getGeneralSettings(): GeneralSettingsSnapshot
    suspend fun updateGeneralSettings(update: GeneralSettingsUpdate): GeneralSettingsSnapshot

    // Verification
    suspend fun getVerificationSettings(): VerificationSettingsSnapshot
    suspend fun updateVerificationSettings(update: VerificationSettingsUpdate): VerificationSettingsSnapshot

    // Relay
    suspend fun getRelaySettings(): RelaySettingsSnapshot
    suspend fun updateRelaySettings(update: RelaySettingsUpdate): RelaySettingsSnapshot

    // Diagnostics
    suspend fun getDiagnosticsSettings(): DiagnosticsSettingsSnapshot
    suspend fun updateDiagnosticsSettings(update: DiagnosticsSettingsUpdate): DiagnosticsSettingsSnapshot

    // Advanced
    suspend fun getAdvancedSnapshot(): AdvancedSettingsSnapshot
    suspend fun updateAdvanced(update: AdvancedSettingsUpdate): AdvancedSettingsSnapshot

    // Special Alert
    suspend fun getSpecialAlertSettings(): SpecialAlertSettingsSnapshot
    suspend fun updateSpecialAlertSettings(update: SpecialAlertSettingsUpdate): SpecialAlertSettingsSnapshot

    // Message Type Gates
    suspend fun getMessageTypeGates(): MessageTypeGateSnapshot
    suspend fun updateMessageTypeGates(update: MessageTypeGateUpdate): MessageTypeGateSnapshot

    // Forward Type Gates
    suspend fun getForwardTypeGates(): ForwardTypeGateSnapshot
    suspend fun updateForwardTypeGates(update: ForwardTypeGateUpdate): ForwardTypeGateSnapshot

    // Record Settings
    suspend fun getRecordSettings(): RecordSettingsSnapshot
    suspend fun updateRecordSettings(update: RecordSettingsUpdate): RecordSettingsSnapshot

    // SMS Blacklist
    suspend fun getSmsBlacklistSettings(): SmsBlacklistSettingsSnapshot
    suspend fun updateSmsBlacklistSettings(update: SmsBlacklistSettingsUpdate): SmsBlacklistSettingsSnapshot

    // SIM Remark
    suspend fun getSimRemarkSettings(): SimRemarkSettingsSnapshot
    suspend fun updateSimRemarkSettings(update: SimRemarkSettingsUpdate): SimRemarkSettingsSnapshot

    // Forward Common Config
    suspend fun loadForwardCommonConfig(): ForwardCommonConfig
    suspend fun saveForwardCommonConfig(config: ForwardCommonConfig)

    // Notify Templates
    suspend fun loadAppNotifyTemplate(): String
    suspend fun saveAppNotifyTemplate(template: String)
    suspend fun loadCallNotifyTemplate(): String
    suspend fun saveCallNotifyTemplate(template: String)

    // User Settings snapshot
    suspend fun getUserSettingsSnapshot(): UserSettingsSnapshot
    suspend fun updateUserSettings(update: UserSettingsUpdate): UserSettingsSnapshot

    // Overview
    suspend fun getOverviewSettings(): OverviewSettingsSnapshot
    suspend fun updateOverviewSettings(update: OverviewSettingsUpdate): OverviewSettingsSnapshot

    // UI Flows
    fun getHazeBlurRadiusFlow(): Flow<Int>
    fun getHazeTintAlphaFlow(): Flow<Float>

    // Auto Update
    suspend fun getAutoUpdateSettings(): AutoUpdateSettingsSnapshot
    suspend fun setIgnoredGithubVersion(versionName: String)

    // Theme / UI Kit / Language / Privacy
    suspend fun getThemeMode(): Int
    suspend fun setThemeMode(mode: Int)
    suspend fun getUiKitStyle(): Int
    suspend fun setUiKitStyle(style: Int)
    suspend fun getLanguageTag(): String
    suspend fun setLanguageTag(languageTag: String)
    suspend fun isPrivacyPolicyAccepted(): Boolean
    suspend fun setPrivacyPolicyAccepted(accepted: Boolean)

    // Battery reminder runtime flags
    suspend fun clearBatteryReminderRuntimeFlags(
        clearLowBatteryBelow: Boolean = false,
        clearFullBatteryAbove: Boolean = false,
        clearChargingState: Boolean = false,
    )
}
