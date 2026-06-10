package io.github.magisk317.relay.platform.ipc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomMessageReceiverPolicyTest {

    @Test
    fun tokenAccepted_requiresExactNonBlankMatch() {
        assertTrue(CustomMessageReceiverPolicy.isTokenAccepted("abc", "abc"))
        assertFalse(CustomMessageReceiverPolicy.isTokenAccepted("abc", ""))
        assertFalse(CustomMessageReceiverPolicy.isTokenAccepted("", "abc"))
        assertFalse(CustomMessageReceiverPolicy.isTokenAccepted(null, "abc"))
    }

    @Test
    fun normalizeTargetSenderIds_filtersInvalidAndDeduplicates() {
        assertEquals(
            listOf(3L, 9L),
            CustomMessageReceiverPolicy.normalizeTargetSenderIds(longArrayOf(3L, 0L, -1L, 9L, 3L)),
        )
        assertNull(CustomMessageReceiverPolicy.normalizeTargetSenderIds(longArrayOf()))
        assertNull(CustomMessageReceiverPolicy.normalizeTargetSenderIds(longArrayOf(0L, -2L)))
    }

    @Test
    fun resolvesPackageAppChannelAndEventDefaults() {
        val packageName = CustomMessageReceiverPolicy.resolvePackageName("", "com.sender.pkg")
        assertEquals("com.sender.pkg", packageName)
        assertEquals("com.sender.pkg", CustomMessageReceiverPolicy.resolveAppName("", packageName))
        assertEquals(
            CustomMessageBroadcastContract.DEFAULT_NOTIFY_CHANNEL_ID,
            CustomMessageReceiverPolicy.resolveNotifyChannelId(""),
        )
        val generatedEventId = CustomMessageReceiverPolicy.resolveEventId(
            eventId = "",
            message = "hello",
            title = "title",
            packageName = packageName,
        )
        assertTrue(generatedEventId.startsWith("custom_"))
        assertEquals(
            "evt_manual",
            CustomMessageReceiverPolicy.resolveEventId(
                eventId = "evt_manual",
                message = "hello",
                title = "title",
                packageName = packageName,
            ),
        )
    }
}
