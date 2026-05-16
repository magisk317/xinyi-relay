package io.github.magisk317.relay.contract.settings

import kotlinx.serialization.Serializable

// ── General ──────────────────────────────────────────────────────────────────

@Serializable
data class GeneralSettingsSnapshot(
    val moduleEnabled: Boolean,
    val accordionMode: Boolean,
)

@Serializable
data class GeneralSettingsUpdate(
    val moduleEnabled: Boolean? = null,
    val accordionMode: Boolean? = null,
)

// ── Verification ─────────────────────────────────────────────────────────────

@Serializable
data class VerificationSettingsSnapshot(
    val verificationFeaturesEnabled: Boolean,
    val copyToClipboard: Boolean,
    val showToast: Boolean,
    val showCodeNotification: Boolean,
    val notificationOwner: String,
    val autoCancelNotification: Boolean,
    val notificationRetentionTime: String,
    val autoInputEnabled: Boolean,
    val autoEnterEnabled: Boolean,
    val autoInputDelay: String,
    val autoInputInterval: String,
    val relayKeywords: String,
    val blockSmsEnabled: Boolean,
)

@Serializable
data class VerificationSettingsUpdate(
    val verificationFeaturesEnabled: Boolean? = null,
    val copyToClipboard: Boolean? = null,
    val showToast: Boolean? = null,
    val showCodeNotification: Boolean? = null,
    val notificationOwner: String? = null,
    val autoCancelNotification: Boolean? = null,
    val notificationRetentionTime: String? = null,
    val autoInputEnabled: Boolean? = null,
    val autoEnterEnabled: Boolean? = null,
    val autoInputDelay: String? = null,
    val autoInputInterval: String? = null,
    val relayKeywords: String? = null,
    val blockSmsEnabled: Boolean? = null,
)

// ── Relay ────────────────────────────────────────────────────────────────────

@Serializable
data class RelaySettingsSnapshot(
    val relayFeaturesEnabled: Boolean,
    val smsForwardDedupWindowSec: Int,
)

@Serializable
data class RelaySettingsUpdate(
    val relayFeaturesEnabled: Boolean? = null,
    val smsForwardDedupWindowSec: Int? = null,
)

// ── Diagnostics ──────────────────────────────────────────────────────────────

@Serializable
data class DiagnosticsSettingsSnapshot(
    val rootDbCatchupEnabled: Boolean,
    val rootDbCatchupIntervalMin: String,
    val rootDbCatchupWriteback: Boolean,
    val forceStopRecoveryEnabled: Boolean,
    val forceStopRecoveryRelaunchOnceEnabled: Boolean,
    val verboseLogMode: Boolean,
    val sensitiveDebugLogMode: Boolean,
    val runtimeLogRetentionDays: Int,
    val autoUpdateOnStart: Boolean,
    val autoUpdateWifiOnly: Boolean,
    val analyticsEnabled: Boolean,
    val keepAliveOomAdj: Boolean,
    val keepAliveAntiKill: Boolean,
    val keepAliveStandbyBypass: Boolean,
    val keepAliveDozeBypass: Boolean,
    val keepAliveAccessibilityHeartbeat: Boolean,
    val keepAliveDedicatedService: Boolean,
)

@Serializable
data class DiagnosticsSettingsUpdate(
    val rootDbCatchupEnabled: Boolean? = null,
    val rootDbCatchupIntervalMin: String? = null,
    val rootDbCatchupWriteback: Boolean? = null,
    val forceStopRecoveryEnabled: Boolean? = null,
    val forceStopRecoveryRelaunchOnceEnabled: Boolean? = null,
    val verboseLogMode: Boolean? = null,
    val sensitiveDebugLogMode: Boolean? = null,
    val runtimeLogRetentionDays: Int? = null,
    val autoUpdateOnStart: Boolean? = null,
    val autoUpdateWifiOnly: Boolean? = null,
    val analyticsEnabled: Boolean? = null,
    val keepAliveOomAdj: Boolean? = null,
    val keepAliveAntiKill: Boolean? = null,
    val keepAliveStandbyBypass: Boolean? = null,
    val keepAliveDozeBypass: Boolean? = null,
    val keepAliveAccessibilityHeartbeat: Boolean? = null,
    val keepAliveDedicatedService: Boolean? = null,
)

// ── Advanced ─────────────────────────────────────────────────────────────────

@Serializable
data class AdvancedSettingsSnapshot(
    val enableSmsBlacklist: Boolean,
)

@Serializable
data class AdvancedSettingsUpdate(
    val enableSmsBlacklist: Boolean? = null,
)

// ── Special Alert ────────────────────────────────────────────────────────────

@Serializable
data class SpecialAlertSettingsSnapshot(
    val lowBatteryReminderEnabled: Boolean,
    val lowBatteryThreshold: Int,
    val lowBatteryChannelId: String,
    val fullBatteryReminderEnabled: Boolean,
    val fullBatteryChannelId: String,
    val chargingChangeReminderEnabled: Boolean,
    val chargingChangeChannelId: String,
    val callAlertLocalEnabled: Boolean,
    val callAlertChannelId: String,
    val smsKeywordEnabled: Boolean,
    val smsKeywordKeywords: String,
    val smsKeywordNotificationEnabled: Boolean,
    val smsKeywordSoundEnabled: Boolean,
    val smsKeywordVibrateEnabled: Boolean,
    val appKeywordEnabled: Boolean,
    val appKeywordKeywords: String,
    val appKeywordNotificationEnabled: Boolean,
    val appKeywordSoundEnabled: Boolean,
    val appKeywordVibrateEnabled: Boolean,
)

@Serializable
data class SpecialAlertSettingsUpdate(
    val lowBatteryReminderEnabled: Boolean? = null,
    val lowBatteryThreshold: Int? = null,
    val lowBatteryChannelId: String? = null,
    val fullBatteryReminderEnabled: Boolean? = null,
    val fullBatteryChannelId: String? = null,
    val chargingChangeReminderEnabled: Boolean? = null,
    val chargingChangeChannelId: String? = null,
    val callAlertLocalEnabled: Boolean? = null,
    val callAlertChannelId: String? = null,
    val smsKeywordEnabled: Boolean? = null,
    val smsKeywordKeywords: String? = null,
    val smsKeywordNotificationEnabled: Boolean? = null,
    val smsKeywordSoundEnabled: Boolean? = null,
    val smsKeywordVibrateEnabled: Boolean? = null,
    val appKeywordEnabled: Boolean? = null,
    val appKeywordKeywords: String? = null,
    val appKeywordNotificationEnabled: Boolean? = null,
    val appKeywordSoundEnabled: Boolean? = null,
    val appKeywordVibrateEnabled: Boolean? = null,
)

// ── Record ───────────────────────────────────────────────────────────────────

@Serializable
data class RecordSettingsSnapshot(
    val previousRecordEnabled: Boolean,
    val codeRecordEnabled: Boolean,
    val plainSmsRecordEnabled: Boolean,
    val appNotifyRecordEnabled: Boolean,
    val callNotifyRecordEnabled: Boolean,
    val codeHistoryLimit: String,
    val plainSmsHistoryLimit: String,
    val appNotifyHistoryLimit: String,
    val callNotifyHistoryLimit: String,
)

@Serializable
data class RecordSettingsUpdate(
    val codeRecordEnabled: Boolean? = null,
    val plainSmsRecordEnabled: Boolean? = null,
    val appNotifyRecordEnabled: Boolean? = null,
    val callNotifyRecordEnabled: Boolean? = null,
    val codeHistoryLimit: String? = null,
    val plainSmsHistoryLimit: String? = null,
    val appNotifyHistoryLimit: String? = null,
    val callNotifyHistoryLimit: String? = null,
)

// ── SMS Blacklist ────────────────────────────────────────────────────────────

@Serializable
data class SmsBlacklistSettingsSnapshot(
    val enabled: Boolean,
    val deleteBlockedSms: Boolean,
    val blockIncomingSms: Boolean,
    val numbers: String,
    val prefixes: String,
    val regexRules: String,
    val contentRules: String,
)

@Serializable
data class SmsBlacklistSettingsUpdate(
    val enabled: Boolean? = null,
    val deleteBlockedSms: Boolean? = null,
    val blockIncomingSms: Boolean? = null,
    val numbers: String? = null,
    val prefixes: String? = null,
    val regexRules: String? = null,
    val contentRules: String? = null,
)

// ── SIM Remark ───────────────────────────────────────────────────────────────

@Serializable
data class SimRemarkSettingsSnapshot(
    val simSlot1Remark: String,
    val simSlot2Remark: String,
)

@Serializable
data class SimRemarkSettingsUpdate(
    val simSlot1Remark: String? = null,
    val simSlot2Remark: String? = null,
)

// ── Message Type Gates ───────────────────────────────────────────────────────

@Serializable
data class MessageTypeGateSnapshot(
    val smsCodeEnabled: Boolean,
    val smsPlainEnabled: Boolean,
    val appNotifyEnabled: Boolean,
    val callNotifyEnabled: Boolean,
)

@Serializable
data class MessageTypeGateUpdate(
    val smsCodeEnabled: Boolean? = null,
    val smsPlainEnabled: Boolean? = null,
    val appNotifyEnabled: Boolean? = null,
    val callNotifyEnabled: Boolean? = null,
)

// ── Forward Type Gates ───────────────────────────────────────────────────────

@Serializable
data class ForwardTypeGateSnapshot(
    val smsCodeEnabled: Boolean,
    val smsPlainEnabled: Boolean,
    val appNotifyEnabled: Boolean,
    val callNotifyEnabled: Boolean,
    val callNotifyFinalEnabled: Boolean,
)

@Serializable
data class ForwardTypeGateUpdate(
    val smsCodeEnabled: Boolean? = null,
    val smsPlainEnabled: Boolean? = null,
    val appNotifyEnabled: Boolean? = null,
    val callNotifyEnabled: Boolean? = null,
    val callNotifyFinalEnabled: Boolean? = null,
)

// ── User Settings ────────────────────────────────────────────────────────────

@Serializable
data class UserSettingsSnapshot(
    val moduleEnabled: Boolean,
    val verificationFeaturesEnabled: Boolean,
    val relayFeaturesEnabled: Boolean,
    val copyToClipboard: Boolean,
    val showToast: Boolean,
    val showCodeNotification: Boolean,
    val blockSmsEnabled: Boolean,
    val enableAutoInputCode: Boolean,
    val enableAutoEnterCode: Boolean,
    val verboseLogMode: Boolean,
    val smsBlacklistEnabled: Boolean,
    val forceStopRecoveryEnabled: Boolean,
)

@Serializable
data class UserSettingsUpdate(
    val moduleEnabled: Boolean? = null,
    val verificationFeaturesEnabled: Boolean? = null,
    val relayFeaturesEnabled: Boolean? = null,
    val copyToClipboard: Boolean? = null,
    val showToast: Boolean? = null,
    val showCodeNotification: Boolean? = null,
    val blockSmsEnabled: Boolean? = null,
    val enableAutoInputCode: Boolean? = null,
    val enableAutoEnterCode: Boolean? = null,
    val verboseLogMode: Boolean? = null,
    val smsBlacklistEnabled: Boolean? = null,
    val forceStopRecoveryEnabled: Boolean? = null,
)

// ── Overview ─────────────────────────────────────────────────────────────────

@Serializable
data class OverviewSettingsSnapshot(
    val cardOrder: String,
    val enabledCardIds: String,
    val chartType: String,
    val chartWindow: String,
)

@Serializable
data class OverviewSettingsUpdate(
    val cardOrder: String? = null,
    val enabledCardIds: String? = null,
    val chartType: String? = null,
    val chartWindow: String? = null,
)

// ── Auto Update ──────────────────────────────────────────────────────────────

@Serializable
data class AutoUpdateSettingsSnapshot(
    val enabled: Boolean,
    val wifiOnly: Boolean,
    val ignoredGithubVersion: String,
)

// ── Remote Agent ─────────────────────────────────────────────────────────────

@Serializable
data class RemoteAgentSnapshot(
    val backendBaseUrl: String,
    val bound: Boolean,
    val userId: Long,
    val deviceId: Long,
    val deviceTokenPresent: Boolean,
    val lastAppliedConfigRevision: Long,
    val syncState: String,
    val lastError: String,
    val lastHeartbeatAt: Long,
    val lastPullAt: Long,
    val lastPushAt: Long,
    val pendingMutations: Int,
)
