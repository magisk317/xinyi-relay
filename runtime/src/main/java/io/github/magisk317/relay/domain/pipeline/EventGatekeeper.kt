package io.github.magisk317.relay.domain.pipeline

import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.common.constant.PrefConst
import io.github.magisk317.relay.diagnostics.ForwardFlowLog
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.data.db.AppDatabase
import io.github.magisk317.relay.domain.event.RelayEvent

data class GateDecision(
    val allowed: Boolean,
    val reason: String,
)

class EventGatekeeper(
    private val db: AppDatabase,
    private val preferenceDataSource: PreferenceDataSource,
) {
    suspend fun check(event: RelayEvent, traceId: String): GateDecision {
        if (!preferenceDataSource.getBoolean(PrefConst.KEY_ENABLE, true)) {
            return GateDecision(false, "module_disabled")
        }
        if (!isMessageTypeEnabled(event.messageType)) {
            return GateDecision(false, "message_type_disabled:${event.messageType.name.lowercase()}")
        }
        if (event.messageType == MessageType.APP_NOTIFY) {
            val pkg = event.packageName.trim()
            if (pkg.isEmpty()) {
                ForwardFlowLog.w(traceId, "App notify gate pkg=<empty> final_decision=drop reason=empty_package")
                return GateDecision(false, "empty_package")
            }
            val appInfo = runCatching { db.appInfoDao().getByPackageName(pkg) }.getOrElse { error ->
                ForwardFlowLog.e(traceId, "App notify gate query failed pkg=$pkg final_decision=drop", error)
                XLog.e("App notify gate query failed for pkg=$pkg", error)
                return GateDecision(false, "app_gate_query_failed")
            }
            if (appInfo == null) {
                ForwardFlowLog.i(
                    traceId,
                    "App notify gate pkg=$pkg state=missing final_decision=allow",
                )
            } else if (appInfo.forwarding != true) {
                ForwardFlowLog.i(
                    traceId,
                    "App notify gate pkg=$pkg state=disabled final_decision=drop",
                )
                return GateDecision(false, "app_source_disabled")
            }
        }
        return GateDecision(true, "allowed")
    }

    private suspend fun isMessageTypeEnabled(messageType: MessageType): Boolean {
        return when (messageType) {
            MessageType.SMS_CODE -> preferenceDataSource.getBoolean(
                PrefConst.KEY_MSG_TYPE_SMS_CODE_ENABLED,
                defaultMessageTypeEnabled(MessageType.SMS_CODE),
            )
            MessageType.SMS_PLAIN -> preferenceDataSource.getBoolean(
                PrefConst.KEY_MSG_TYPE_SMS_PLAIN_ENABLED,
                defaultMessageTypeEnabled(MessageType.SMS_PLAIN),
            )
            MessageType.APP_NOTIFY -> preferenceDataSource.getBoolean(
                PrefConst.KEY_MSG_TYPE_APP_NOTIFY_ENABLED,
                defaultMessageTypeEnabled(MessageType.APP_NOTIFY),
            )
            MessageType.CALL_NOTIFY -> preferenceDataSource.getBoolean(
                PrefConst.KEY_MSG_TYPE_CALL_NOTIFY_ENABLED,
                defaultMessageTypeEnabled(MessageType.CALL_NOTIFY),
            )
        }
    }

    private fun defaultMessageTypeEnabled(messageType: MessageType): Boolean {
        return when (messageType) {
            MessageType.SMS_CODE -> true
            MessageType.SMS_PLAIN -> true
            MessageType.APP_NOTIFY -> true
            MessageType.CALL_NOTIFY -> false
        }
    }
}
