package io.github.magisk317.relay.platform.ipc

object IpcPayloadLimits {
    const val MAX_MESSAGE_BYTES = 64 * 1024
    const val MAX_TITLE_BYTES = 4 * 1024
    const val MAX_PACKAGE_NAME_BYTES = 512
    const val MAX_CHANNEL_ID_BYTES = 1024
    const val MAX_EVENT_ID_BYTES = 1024
    const val MAX_TARGET_SENDER_IDS = 256
    const val MAX_APP_ICON_BYTES = 256 * 1024

    private const val MAX_SHORT_METADATA_BYTES = 4 * 1024
    private const val MAX_PROTOCOL_FIELD_BYTES = 256

    fun validateForward(payload: ForwardBroadcastPayload): String? = when {
        payload.body.exceedsUtf8Bytes(MAX_MESSAGE_BYTES) -> "body_too_large"
        payload.sender.exceedsUtf8Bytes(MAX_TITLE_BYTES) -> "sender_too_large"
        payload.company.exceedsUtf8Bytes(MAX_SHORT_METADATA_BYTES) -> "company_too_large"
        payload.smsCode.exceedsUtf8Bytes(MAX_PROTOCOL_FIELD_BYTES) -> "sms_code_too_large"
        payload.packageName.exceedsUtf8Bytes(MAX_PACKAGE_NAME_BYTES) -> "package_name_too_large"
        payload.notifyChannelId.exceedsUtf8Bytes(MAX_CHANNEL_ID_BYTES) -> "channel_id_too_large"
        payload.eventId.exceedsUtf8Bytes(MAX_EVENT_ID_BYTES) -> "event_id_too_large"
        payload.msgType.exceedsUtf8Bytes(MAX_PROTOCOL_FIELD_BYTES) -> "message_type_too_large"
        payload.forwardSource.exceedsUtf8Bytes(MAX_PROTOCOL_FIELD_BYTES) -> "forward_source_too_large"
        payload.callStage.exceedsUtf8Bytes(MAX_PROTOCOL_FIELD_BYTES) -> "call_stage_too_large"
        payload.appIcon.exceedsUtf8Bytes(MAX_APP_ICON_BYTES) -> "app_icon_too_large"
        else -> null
    }

    fun validateCustom(
        payload: CustomMessageBroadcastPayload,
        rawTargetSenderIdCount: Int,
    ): String? = when {
        payload.message.exceedsUtf8Bytes(MAX_MESSAGE_BYTES) -> "message_too_large"
        payload.title.exceedsUtf8Bytes(MAX_TITLE_BYTES) -> "title_too_large"
        payload.appName.exceedsUtf8Bytes(MAX_SHORT_METADATA_BYTES) -> "app_name_too_large"
        payload.packageName.exceedsUtf8Bytes(MAX_PACKAGE_NAME_BYTES) -> "package_name_too_large"
        payload.notifyChannelId.exceedsUtf8Bytes(MAX_CHANNEL_ID_BYTES) -> "channel_id_too_large"
        payload.eventId.exceedsUtf8Bytes(MAX_EVENT_ID_BYTES) -> "event_id_too_large"
        rawTargetSenderIdCount > MAX_TARGET_SENDER_IDS -> "target_sender_ids_too_large"
        else -> null
    }

    private fun String?.exceedsUtf8Bytes(maxBytes: Int): Boolean {
        if (this == null) return false
        return toByteArray(Charsets.UTF_8).size > maxBytes
    }
}
