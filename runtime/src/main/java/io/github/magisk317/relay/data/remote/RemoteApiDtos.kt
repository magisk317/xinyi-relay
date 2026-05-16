package io.github.magisk317.relay.data.remote

import io.github.magisk317.relay.android.data.db.entity.AppInfo
import io.github.magisk317.relay.android.data.db.entity.NotifyRouteRule
import io.github.magisk317.relay.android.data.db.entity.SmsCodeRule
import io.github.magisk317.relay.contract.model.ForwardCommonConfig
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
import io.github.magisk317.relay.engine.model.ForwardFilterRule
import io.github.magisk317.relay.engine.model.Rule
import io.github.magisk317.relay.engine.model.Sender
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

internal data class RemoteConfigPayload(
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
    val senders: List<Sender>,
    val rules: List<Rule>,
    val smsCodeRules: List<SmsCodeRule>,
    val appInfos: List<AppInfo>,
    val notifyRoutes: List<NotifyRouteRule>,
    val forwardFilters: List<ForwardFilterRule>,
)

@Serializable
internal data class AgentRegisterRequest(
    val bindCode: String,
    val deviceName: String,
    val deviceModel: String,
    val platform: String,
    val appVersion: String,
)

@Serializable
internal data class AgentRegisterResponse(
    val userId: Long = 0L,
    val deviceId: Long = 0L,
    val deviceToken: String = "",
)

@Serializable
internal data class HeartbeatRequest(
    val appVersion: String,
    val localAddresses: List<String>,
    val capabilities: Map<String, Boolean>,
)

@Serializable
internal data class ConfigSnapshotRequest(
    @SerialName("base_revision")
    val baseRevision: Long,
    val snapshot: JsonObject,
)

@Serializable
internal data class ConfigSnapshotResponse(
    val revision: Long = 0L,
    val snapshot: JsonObject? = null,
)

@Serializable
internal data class RelayRecordWire(
    val eventId: String,
    val recordType: String,
    val sender: String,
    val body: String,
    val smsCode: String,
    val packageName: String,
    val msgType: Int,
    val callType: Int,
    val occurredAt: String,
    val metadata: JsonObject,
)

@Serializable
internal data class RelayRecordsBatchRequest(
    val records: List<RelayRecordWire>,
)
