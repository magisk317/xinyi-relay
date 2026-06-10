package io.github.magisk317.relay.platform.ipc

import android.content.Intent

object CustomMessageBroadcastContract {
    const val EXTRA_IPC_TOKEN = "ipc_token"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_TITLE = "title"
    const val EXTRA_APP_NAME = "app_name"
    const val EXTRA_PACKAGE_NAME = "package_name"
    const val EXTRA_NOTIFY_CHANNEL_ID = "notify_channel_id"
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_TARGET_SENDER_IDS = "target_sender_ids"

    const val SOURCE_CUSTOM_BROADCAST = "custom_broadcast"
    const val DEFAULT_NOTIFY_CHANNEL_ID = SOURCE_CUSTOM_BROADCAST

    fun populatePayload(
        intent: Intent,
        message: String,
        title: String? = null,
        appName: String? = null,
        packageName: String? = null,
        notifyChannelId: String? = null,
        eventId: String? = null,
        targetSenderIds: LongArray? = null,
    ) {
        intent.putExtra(EXTRA_MESSAGE, message)
        title?.let { intent.putExtra(EXTRA_TITLE, it) }
        appName?.let { intent.putExtra(EXTRA_APP_NAME, it) }
        packageName?.let { intent.putExtra(EXTRA_PACKAGE_NAME, it) }
        notifyChannelId?.let { intent.putExtra(EXTRA_NOTIFY_CHANNEL_ID, it) }
        eventId?.let { intent.putExtra(EXTRA_EVENT_ID, it) }
        targetSenderIds?.let { intent.putExtra(EXTRA_TARGET_SENDER_IDS, it) }
    }

    fun putIpcToken(intent: Intent, token: String?) {
        if (!token.isNullOrBlank()) {
            intent.putExtra(EXTRA_IPC_TOKEN, token)
        }
    }
}
