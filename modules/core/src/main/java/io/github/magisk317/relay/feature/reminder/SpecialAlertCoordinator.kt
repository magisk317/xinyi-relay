package io.github.magisk317.relay.feature.reminder

import android.content.Context
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.android.diagnostics.ForwardFlowLog
import io.github.magisk317.relay.engine.event.RelayEvent
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import io.github.magisk317.xposed.logging.MagiskOtel

object SpecialAlertCoordinator {
    suspend fun notifyForEvent(
        context: Context,
        event: RelayEvent,
        traceId: String? = null,
    ) {
        when (event.messageType) {
            MessageType.SMS_CODE,
            MessageType.SMS_PLAIN,
            MessageType.APP_NOTIFY,
            -> {
                val decision = RuntimeGraph.from(context).eventGatekeeper.check(event, traceId.orEmpty())
                if (!decision.allowed) {
                    ForwardFlowLog.i(
                        traceId,
                        "Special alert skipped by gate type=${event.messageType.name.lowercase()} reason=${decision.reason}",
                    )
                    emit(
                        result = "skip",
                        reason = decision.reason.ifBlank { "gate_denied" },
                        msgType = event.messageType.name.lowercase(),
                    )
                    return
                }
                when (event.messageType) {
                    MessageType.SMS_CODE,
                    MessageType.SMS_PLAIN,
                    -> SpecialAlertNotifier.notifySmsKeywordAlert(
                        context = context,
                        sender = event.sender,
                        body = event.body,
                        company = event.companyOrAppName,
                    )

                    MessageType.APP_NOTIFY -> SpecialAlertNotifier.notifyAppKeywordAlert(
                        context = context,
                        appName = event.companyOrAppName,
                        title = event.sender,
                        body = event.body,
                    )

                    else -> Unit
                }
                emit(
                    result = "ok",
                    reason = "notified",
                    msgType = event.messageType.name.lowercase(),
                )
            }

            MessageType.CALL_NOTIFY -> {
                val enabled = RuntimeSettingsCache.getSpecialAlertSettings(
                    RuntimeGraph.from(context).settingsRepository,
                ).callAlertLocalEnabled
                if (!enabled) {
                    emit(result = "skip", reason = "call_local_disabled", msgType = "call_notify")
                    return
                }
                SpecialAlertNotifier.notifyIncomingCallAlert(
                    context = context,
                    display = event.sender,
                )
                emit(result = "ok", reason = "call_notified", msgType = "call_notify")
            }
        }
    }

    private fun emit(result: String, reason: String, msgType: String) {
        MagiskOtel.event(
            name = "app.alert",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "special_alert",
                "reason" to reason.take(64),
                "msg_type" to msgType,
            ),
            statusOk = true,
        )
    }
}
