package io.github.magisk317.relay.platform.ipc

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Feature: standard-mode-fallback, Property 5: Notification-to-Payload Field Preservation
 *
 * **Validates: Requirements 4.2**
 *
 * For any `StatusBarNotification` with non-empty package name, title, and body text,
 * `AppNotificationIngressAdapter.toPayload()` SHALL produce a `ForwardBroadcastPayload`
 * where `payload.packageName == sbn.packageName`, the sender contains the notification title,
 * and the body contains the notification text.
 */
class NotificationPayloadPropertyTest : FunSpec({

    test("Property 5: appNotificationPayload preserves packageName, sender contains title, body contains text").config(
        invocations = 100,
    ) {
        checkAll(
            1,
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

            payload.packageName shouldBe packageName
            payload.sender shouldContain title
            payload.body shouldContain body
            payload.msgType shouldBe ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY
        }
    }
})
