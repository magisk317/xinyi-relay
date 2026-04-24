package io.github.magisk317.relay.platform.ipc

import android.content.Intent
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.domain.event.RelayEvent

data class CustomMessageBroadcastPayload(
    val message: String = "",
    val title: String = "",
    val appName: String = "",
    val packageName: String = "",
    val notifyChannelId: String = "",
    val eventId: String = "",
    val targetSenderIds: List<Long>? = null,
) {
    fun toRelayEvent(sentFromPackage: String? = null): RelayEvent {
        val resolvedPackageName = CustomMessageReceiverPolicy.resolvePackageName(packageName, sentFromPackage)
        val resolvedTitle = title.trim()
        val resolvedMessage = message
        val resolvedAppName = CustomMessageReceiverPolicy.resolveAppName(appName, resolvedPackageName)
        val resolvedNotifyChannelId = CustomMessageReceiverPolicy.resolveNotifyChannelId(notifyChannelId)
        val resolvedEventId = CustomMessageReceiverPolicy.resolveEventId(
            eventId = eventId,
            message = resolvedMessage,
            title = resolvedTitle,
            packageName = resolvedPackageName,
        )
        return RelayEvent(
            messageType = MessageType.APP_NOTIFY,
            sourceType = CustomMessageBroadcastContract.SOURCE_CUSTOM_BROADCAST,
            sender = resolvedTitle,
            body = resolvedMessage,
            timestamp = System.currentTimeMillis(),
            packageName = resolvedPackageName,
            notifyChannelId = resolvedNotifyChannelId,
            companyOrAppName = resolvedAppName,
            smsCode = null,
            callType = 0,
            callStage = "",
            simSlot = -1,
            subId = 0,
            targetSenderIds = targetSenderIds,
        ).copy(
            // eventId is traced externally; keep relay payload deterministic
            sourceType = CustomMessageBroadcastContract.SOURCE_CUSTOM_BROADCAST,
        )
    }

    companion object {
        fun fromIntent(intent: Intent): CustomMessageBroadcastPayload {
            return CustomMessageBroadcastPayload(
                message = intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_MESSAGE).orEmpty(),
                title = intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_TITLE).orEmpty(),
                appName = intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_APP_NAME).orEmpty(),
                packageName = intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_PACKAGE_NAME).orEmpty(),
                notifyChannelId = intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_NOTIFY_CHANNEL_ID).orEmpty(),
                eventId = intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_EVENT_ID).orEmpty(),
                targetSenderIds = CustomMessageReceiverPolicy.normalizeTargetSenderIds(
                    intent.getLongArrayExtra(CustomMessageBroadcastContract.EXTRA_TARGET_SENDER_IDS),
                ),
            )
        }
    }
}
