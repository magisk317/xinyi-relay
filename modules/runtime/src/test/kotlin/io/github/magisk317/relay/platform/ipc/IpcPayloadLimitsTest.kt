package io.github.magisk317.relay.platform.ipc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class IpcPayloadLimitsTest {
    @Test
    fun validateForward_acceptsExactLimitsAndRejectsOversizedFields() {
        assertNull(
            IpcPayloadLimits.validateForward(
                ForwardBroadcastPayload(
                    body = "b".repeat(IpcPayloadLimits.MAX_MESSAGE_BYTES),
                    sender = "s".repeat(IpcPayloadLimits.MAX_TITLE_BYTES),
                    packageName = "p".repeat(IpcPayloadLimits.MAX_PACKAGE_NAME_BYTES),
                    notifyChannelId = "c".repeat(IpcPayloadLimits.MAX_CHANNEL_ID_BYTES),
                    eventId = "e".repeat(IpcPayloadLimits.MAX_EVENT_ID_BYTES),
                    appIcon = "i".repeat(IpcPayloadLimits.MAX_APP_ICON_BYTES),
                ),
            ),
        )
        assertEquals(
            "body_too_large",
            IpcPayloadLimits.validateForward(
                ForwardBroadcastPayload(body = "b".repeat(IpcPayloadLimits.MAX_MESSAGE_BYTES + 1)),
            ),
        )
        assertEquals(
            "sender_too_large",
            IpcPayloadLimits.validateForward(
                ForwardBroadcastPayload(sender = "s".repeat(IpcPayloadLimits.MAX_TITLE_BYTES + 1)),
            ),
        )
        assertEquals(
            "package_name_too_large",
            IpcPayloadLimits.validateForward(
                ForwardBroadcastPayload(packageName = "p".repeat(IpcPayloadLimits.MAX_PACKAGE_NAME_BYTES + 1)),
            ),
        )
        assertEquals(
            "channel_id_too_large",
            IpcPayloadLimits.validateForward(
                ForwardBroadcastPayload(notifyChannelId = "c".repeat(IpcPayloadLimits.MAX_CHANNEL_ID_BYTES + 1)),
            ),
        )
        assertEquals(
            "event_id_too_large",
            IpcPayloadLimits.validateForward(
                ForwardBroadcastPayload(eventId = "e".repeat(IpcPayloadLimits.MAX_EVENT_ID_BYTES + 1)),
            ),
        )
        assertEquals(
            "app_icon_too_large",
            IpcPayloadLimits.validateForward(
                ForwardBroadcastPayload(appIcon = "i".repeat(IpcPayloadLimits.MAX_APP_ICON_BYTES + 1)),
            ),
        )
    }

    @Test
    fun validateCustom_rejectsOversizedMessageTitleAndTargets() {
        assertEquals(
            "message_too_large",
            IpcPayloadLimits.validateCustom(
                payload = CustomMessageBroadcastPayload(
                    message = "m".repeat(IpcPayloadLimits.MAX_MESSAGE_BYTES + 1),
                ),
                rawTargetSenderIdCount = 0,
            ),
        )
        assertEquals(
            "title_too_large",
            IpcPayloadLimits.validateCustom(
                payload = CustomMessageBroadcastPayload(
                    message = "message",
                    title = "t".repeat(IpcPayloadLimits.MAX_TITLE_BYTES + 1),
                ),
                rawTargetSenderIdCount = 0,
            ),
        )
        assertEquals(
            "target_sender_ids_too_large",
            IpcPayloadLimits.validateCustom(
                payload = CustomMessageBroadcastPayload(message = "message"),
                rawTargetSenderIdCount = IpcPayloadLimits.MAX_TARGET_SENDER_IDS + 1,
            ),
        )
    }

    @Test
    fun validateForward_countsUtf8BytesInsteadOfCharacters() {
        val multiByteBody = "\u4e2d".repeat(IpcPayloadLimits.MAX_MESSAGE_BYTES / 3 + 1)

        assertEquals(
            "body_too_large",
            IpcPayloadLimits.validateForward(ForwardBroadcastPayload(body = multiByteBody)),
        )
    }
}
