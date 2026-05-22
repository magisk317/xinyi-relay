package io.github.magisk317.relay.platform.ipc

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.github.magisk317.relay.contract.constant.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CustomMessageBroadcastPayloadTest {

    @Test
    fun fromIntent_readsCustomMessageExtras() {
        val intent = mockk<Intent>(relaxed = true)
        every { intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_MESSAGE) } returns "hello"
        every { intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_TITLE) } returns "custom title"
        every { intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_APP_NAME) } returns "ADB"
        every { intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_PACKAGE_NAME) } returns "com.example.tool"
        every { intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_NOTIFY_CHANNEL_ID) } returns "manual"
        every { intent.getStringExtra(CustomMessageBroadcastContract.EXTRA_EVENT_ID) } returns "custom_event"
        every { intent.getLongArrayExtra(CustomMessageBroadcastContract.EXTRA_TARGET_SENDER_IDS) } returns longArrayOf(7L, 9L, 9L)

        val restored = CustomMessageBroadcastPayload.fromIntent(intent)

        assertEquals("hello", restored.message)
        assertEquals("custom title", restored.title)
        assertEquals("ADB", restored.appName)
        assertEquals("com.example.tool", restored.packageName)
        assertEquals("manual", restored.notifyChannelId)
        assertEquals("custom_event", restored.eventId)
        assertEquals(listOf(7L, 9L), restored.targetSenderIds)
    }

    @Test
    fun toRelayEvent_mapsIntoAppNotifyPipeline() {
        val payload = CustomMessageBroadcastPayload(
            message = "hello",
            title = "custom title",
            appName = "",
            packageName = "",
            notifyChannelId = "",
            eventId = "",
            targetSenderIds = listOf(7L, 9L),
        )

        val event = payload.toRelayEvent(sentFromPackage = "com.example.tool")

        assertEquals(MessageType.APP_NOTIFY, event.messageType)
        assertEquals(CustomMessageBroadcastContract.SOURCE_CUSTOM_BROADCAST, event.sourceType)
        assertEquals("custom title", event.sender)
        assertEquals("hello", event.body)
        assertEquals("com.example.tool", event.packageName)
        assertEquals("com.example.tool", event.companyOrAppName)
        assertEquals(CustomMessageBroadcastContract.DEFAULT_NOTIFY_CHANNEL_ID, event.notifyChannelId)
        assertEquals(listOf(7L, 9L), event.targetSenderIds)
        assertEquals(-1, event.simSlot)
        assertEquals(0, event.subId)
    }

    @Test
    fun toRelayEvent_prefersExplicitAppFields() {
        val payload = CustomMessageBroadcastPayload(
            message = "hello",
            title = "",
            appName = "ADB",
            packageName = "com.example.explicit",
            notifyChannelId = "custom_channel",
            eventId = "evt_manual",
        )

        val event = payload.toRelayEvent(sentFromPackage = "com.sender.pkg")

        assertEquals("", event.sender)
        assertEquals("com.example.explicit", event.packageName)
        assertEquals("ADB", event.companyOrAppName)
        assertEquals("custom_channel", event.notifyChannelId)
        assertNull(event.targetSenderIds)
    }
}
