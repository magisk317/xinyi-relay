package io.github.magisk317.relay.platform.ipc

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Property 5: Notification-to-Payload Field Preservation
 *
 * **Validates: Requirements 4.2**
 *
 * For any `StatusBarNotification` with non-empty package name, title, and body text,
 * `AppNotificationIngressAdapter.toPayload()` SHALL produce a `ForwardBroadcastPayload`
 * where `payload.packageName == sbn.packageName`, the sender contains the notification title,
 * and the body contains the notification text.
 */
class NotificationPayloadPropertyTest {

    @Test
    fun `Property 5 - appNotificationPayload preserves packageName, sender contains title, body contains text`() {
        runBlocking {
            checkAll(
                100,
                Arb.string(1..100),
                Arb.string(1..100),
                Arb.string(1..200),
                Arb.long(1L..Long.MAX_VALUE),
            ) { packageName, title, body, timestamp ->
                val payload = ForwardPayloadFactory.appNotificationPayload(
                    packageName = packageName,
                    title = title,
                    body = body,
                    timestamp = timestamp,
                    appName = "TestApp",
                    notifyChannelId = "test_channel",
                )

                assertEquals(packageName, payload.packageName)
                assertTrue(payload.sender?.contains(title) == true, "sender <${payload.sender}> should contain title <$title>")
                assertTrue(payload.body?.contains(body) == true, "body <${payload.body}> should contain <$body>")
                assertEquals(ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY, payload.msgType)
            }
        }
    }
}
