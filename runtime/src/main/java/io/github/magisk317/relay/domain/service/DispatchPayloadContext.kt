package io.github.magisk317.relay.domain.service

import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.domain.event.RelayEvent
import io.github.magisk317.relay.model.MsgInfo
import java.util.Date

data class DispatchPayloadContext(
    val appName: String,
    val title: String,
    val message: String,
) {
    fun toMsgInfo(
        event: RelayEvent,
        content: String = event.body,
    ): MsgInfo {
        return MsgInfo(
            type = event.messageType.runtimeType,
            from = event.sender,
            content = content,
            date = Date(event.timestamp),
            simInfo = event.companyOrAppName,
            simSlot = event.simSlot,
            subId = event.subId,
            callType = event.callType,
            packageName = event.packageName,
            notifyChannelId = event.notifyChannelId,
            appName = appName,
            title = title,
            message = message,
            contactName = event.contactName,
            phoneArea = event.phoneArea,
        )
    }

    companion object {
        fun from(event: RelayEvent): DispatchPayloadContext {
            return if (event.messageType == MessageType.APP_NOTIFY) {
                DispatchPayloadContext(
                    appName = event.companyOrAppName,
                    title = event.sender,
                    message = event.body,
                )
            } else {
                DispatchPayloadContext(
                    appName = "",
                    title = "",
                    message = "",
                )
            }
        }
    }
}
