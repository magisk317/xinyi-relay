package io.github.magisk317.relay.web

import io.github.magisk317.relay.domain.sender.SenderType
import kotlinx.serialization.Serializable

@Serializable
internal data class HealthResponse(val status: String)

@Serializable
internal data class ErrorResponse(val error: String)

@Serializable
internal data class SimpleOkResponse(val ok: Boolean)

@Serializable
internal data class LoginPayload(
    val username: String = "",
    val password: String = "",
)

@Serializable
internal data class LoginResponse(
    val authenticated: Boolean,
    val username: String,
    val csrfToken: String,
)

@Serializable
internal data class MeResponse(
    val authenticated: Boolean,
    val username: String? = null,
    val csrfToken: String? = null,
)

@Serializable
internal data class AppUpdatePayload(
    val blocked: Boolean? = null,
    val forwarding: Boolean? = null,
    val notifyTemplate: String? = null,
)

@Serializable
internal data class AdvancedState(
    val enableSmsBlacklist: Boolean,
    val webUiLanAccess: Boolean,
    val senderTotal: Int,
    val senderEnabled: Int,
    val senderAppNotifyEnabled: Int,
)

@Serializable
internal data class AdvancedUpdatePayload(
    val enableSmsBlacklist: Boolean? = null,
    val webUiLanAccess: Boolean? = null,
)

@Serializable
internal data class SettingsState(
    val moduleEnabled: Boolean,
    val verificationFeaturesEnabled: Boolean,
    val relayFeaturesEnabled: Boolean,
    val copyToClipboard: Boolean,
    val showToast: Boolean,
    val showCodeNotification: Boolean,
    val enableAutoInputCode: Boolean,
    val enableAutoEnterCode: Boolean,
    val verboseLogMode: Boolean,
    val smsBlacklistEnabled: Boolean,
    val forceStopRecoveryEnabled: Boolean,
)

@Serializable
internal data class SettingsUpdatePayload(
    val moduleEnabled: Boolean? = null,
    val verificationFeaturesEnabled: Boolean? = null,
    val relayFeaturesEnabled: Boolean? = null,
    val copyToClipboard: Boolean? = null,
    val showToast: Boolean? = null,
    val showCodeNotification: Boolean? = null,
    val enableAutoInputCode: Boolean? = null,
    val enableAutoEnterCode: Boolean? = null,
    val verboseLogMode: Boolean? = null,
    val smsBlacklistEnabled: Boolean? = null,
    val forceStopRecoveryEnabled: Boolean? = null,
)

@Serializable
internal data class VersionState(
    val localVersionName: String,
    val localVersionCode: Int,
    val latestVersionName: String? = null,
    val latestVersionCode: Long? = null,
    val releaseUrl: String? = null,
    val updateAvailable: Boolean? = null,
    val status: String,
    val message: String? = null,
    val checkedAt: Long,
)

@Serializable
internal data class InterceptState(
    val smsBlacklistNumbers: String,
    val smsBlacklistPrefixes: String,
    val smsBlacklistRegex: String,
    val smsBlacklistContent: String,
    val smsBlacklistActionDelete: Boolean,
    val smsBlacklistActionBlock: Boolean,
)

@Serializable
internal data class InterceptUpdatePayload(
    val smsBlacklistNumbers: String? = null,
    val smsBlacklistPrefixes: String? = null,
    val smsBlacklistRegex: String? = null,
    val smsBlacklistContent: String? = null,
    val smsBlacklistActionDelete: Boolean? = null,
    val smsBlacklistActionBlock: Boolean? = null,
)

@Serializable
internal data class SenderItem(
    val id: Long,
    val name: String,
    val type: Int,
    val typeLabel: String,
    val jsonSetting: String,
    val status: Boolean,
    val receiveCode: Boolean,
    val receiveNonCode: Boolean,
    val receiveAppNotify: Boolean,
    val receiveCallNotify: Boolean,
)

@Serializable
internal data class SenderCreatePayload(
    val name: String = "",
    val type: Int = SenderType.WEBHOOK,
    val jsonSetting: String = "",
    val status: Boolean = true,
    val receiveCode: Boolean = true,
    val receiveNonCode: Boolean = true,
    val receiveAppNotify: Boolean = true,
    val receiveCallNotify: Boolean = false,
)

@Serializable
internal data class SenderUpdatePayload(
    val name: String? = null,
    val type: Int? = null,
    val jsonSetting: String? = null,
    val status: Boolean? = null,
    val receiveCode: Boolean? = null,
    val receiveNonCode: Boolean? = null,
    val receiveAppNotify: Boolean? = null,
    val receiveCallNotify: Boolean? = null,
)

@Serializable
internal data class AppItem(
    val packageName: String,
    val label: String,
    val blocked: Boolean,
    val forwarding: Boolean,
    val notifyTemplate: String,
)

@Serializable
internal data class RecordItem(
    val id: Long,
    val date: Long,
    val sender: String,
    val body: String,
    val smsCode: String,
    val packageName: String,
    val msgType: Int,
    val callType: Int,
    val forwardStatus: Int,
    val forwardTarget: String,
    val forwardMessage: String,
)

@Serializable
internal data class OverviewState(
    val appCount: Int,
    val blockedCount: Int,
    val forwardingCount: Int,
    val recordCount: Int,
    val senderTotal: Int,
    val senderEnabled: Int,
    val senderAppNotifyEnabled: Int,
    val version: VersionState,
)

@Serializable
internal data class AnalyticsResponse(
    val allTime: AnalyticsWindow,
    val last7d: AnalyticsWindow,
    val last30d: AnalyticsWindow,
)

@Serializable
internal data class AnalyticsWindow(
    val summary: AnalyticsSummary,
    val senderStats: List<SenderTypeStat>,
)

@Serializable
internal data class AnalyticsSummary(
    val smsCodeDetected: Long,
    val autoInputAttempt: Long,
    val autoInputSuccess: Long,
    val autoInputFail: Long,
    val messageTotal: Long,
)

@Serializable
internal data class SenderTypeStat(
    val senderType: Int,
    val senderTypeLabel: String,
    val configured: Int,
    val enabled: Int,
    val sent: Long,
    val success: Long,
    val failed: Long,
)
