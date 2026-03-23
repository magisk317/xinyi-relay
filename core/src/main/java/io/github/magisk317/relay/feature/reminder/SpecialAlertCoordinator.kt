package io.github.magisk317.relay.feature.reminder

import android.content.Context
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.diagnostics.ForwardFlowLog
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.bootstrap.RuntimeGraph
import io.github.magisk317.relay.domain.system.RuntimeSettingsCache
import kotlinx.coroutines.runBlocking

object SpecialAlertCoordinator {
    fun notifyForEvent(
        context: Context,
        event: RelayEvent,
        traceId: String? = null,
    ) {
        when (event.messageType) {
            MessageType.SMS_CODE,
            MessageType.SMS_PLAIN,
            MessageType.APP_NOTIFY,
            -> {
                val decision = runBlocking {
                    RuntimeGraph.from(context).eventGatekeeper.check(event, traceId.orEmpty())
                }
                if (!decision.allowed) {
                    ForwardFlowLog.i(
                        traceId,
                        "Special alert skipped by gate type=${event.messageType.name.lowercase()} reason=${decision.reason}",
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
            }

            MessageType.CALL_NOTIFY -> {
                val enabled = runBlocking {
                    RuntimeSettingsCache.getSpecialAlertSettings(
                        RuntimeGraph.from(context).settingsRepository,
                    ).callAlertLocalEnabled
                }
                if (!enabled) return
                SpecialAlertNotifier.notifyIncomingCallAlert(
                    context = context,
                    display = event.sender,
                )
            }
        }
    }
}
