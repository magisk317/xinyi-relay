package io.github.magisk317.relay.contract.model

import io.github.magisk317.relay.contract.settings.AdvancedSettingsSnapshot
import io.github.magisk317.relay.contract.settings.DiagnosticsSettingsSnapshot
import io.github.magisk317.relay.contract.settings.ForwardTypeGateSnapshot
import io.github.magisk317.relay.contract.settings.GeneralSettingsSnapshot
import io.github.magisk317.relay.contract.settings.MessageTypeGateSnapshot
import io.github.magisk317.relay.contract.settings.OverviewSettingsSnapshot
import io.github.magisk317.relay.contract.settings.RecordSettingsSnapshot
import io.github.magisk317.relay.contract.settings.RelaySettingsSnapshot
import io.github.magisk317.relay.contract.settings.SimRemarkSettingsSnapshot
import io.github.magisk317.relay.contract.settings.SmsBlacklistSettingsSnapshot
import io.github.magisk317.relay.contract.settings.SpecialAlertSettingsSnapshot
import io.github.magisk317.relay.contract.settings.VerificationSettingsSnapshot
import kotlinx.serialization.Serializable

@Serializable
data class LocalConfigMirrorPayload(
    val general: GeneralSettingsSnapshot,
    val verification: VerificationSettingsSnapshot,
    val relay: RelaySettingsSnapshot,
    val diagnostics: DiagnosticsSettingsSnapshot,
    val advanced: AdvancedSettingsSnapshot,
    val specialAlerts: SpecialAlertSettingsSnapshot,
    val messageTypeGates: MessageTypeGateSnapshot,
    val forwardTypeGates: ForwardTypeGateSnapshot,
    val records: RecordSettingsSnapshot,
    val smsBlacklist: SmsBlacklistSettingsSnapshot,
    val simRemarks: SimRemarkSettingsSnapshot,
    val forwardCommon: ForwardCommonConfig,
    val appNotifyTemplate: String,
    val callNotifyTemplate: String,
    val overview: OverviewSettingsSnapshot,
    val senders: List<SnapshotSender>,
    val rules: List<SnapshotRule>,
    val smsCodeRules: List<SnapshotSmsCodeRule>,
    val deviceAppInfos: Map<String, List<SnapshotAppInfo>>? = null,
    val notifyRoutes: List<SnapshotNotifyRouteRule>,
    val forwardFilters: List<SnapshotForwardFilterRule>,
)

@Serializable
data class SnapshotSender(
    val id: Long = 0L,
    val type: Int = 1,
    val name: String = "",
    val jsonSetting: String = "",
    val status: Int = 1,
    val time: Long = 0L,
    val receiveCode: Int = 1,
    val receiveNonCode: Int = 0,
    val receiveAppNotify: Int = 1,
    val receiveCallNotify: Int = 0,
    val activeSchedule: SenderActiveSchedule = SenderActiveSchedule(),
    val priority: Int = 0,
    val customTemplate: String = "",
)

@Serializable
data class SnapshotRule(
    val id: Long = 0L,
    val type: String = "sms",
    val filed: String = "transpond_all",
    val check: String = "is",
    val value: String = "",
    val senderId: Long = 0L,
    val smsTemplate: String = "",
    val regexReplace: String = "",
    val simSlot: String = "",
    val status: Int = 1,
    val time: Long = 0L,
    val senderList: List<SnapshotSender> = emptyList(),
    val senderLogic: String = "ALL",
    val silentPeriodStart: Int = 0,
    val silentPeriodEnd: Int = 0,
    val silentDayOfWeek: String = "",
    val title: String = "",
)

@Serializable
data class SnapshotAppInfo(
    val packageName: String = "",
    val label: String? = null,
    val blocked: Boolean = false,
    val forwarding: Boolean = false,
    val forwardingConfigured: Boolean = false,
    val notifyTemplate: String = "",
)

@Serializable
data class SnapshotSmsCodeRule(
    val company: String? = null,
    val codeKeyword: String = "",
    val codeRegex: String = "",
    val id: Long = 0L,
)

@Serializable
data class SnapshotNotifyRouteRule(
    val id: Long = 0L,
    val scope: Int,
    val packageName: String,
    val senderId: Long,
    val updateTime: Long = 0L,
)

@Serializable
data class SnapshotForwardFilterRule(
    val id: Long = 0L,
    val msgType: String,
    val scopeType: String,
    val scopeKey: String = "",
    val senderId: Long = 0L,
    val policy: String,
    val matchMode: String,
    val pattern: String,
    val enabled: Int = 1,
    val updateTime: Long = 0L,
)
