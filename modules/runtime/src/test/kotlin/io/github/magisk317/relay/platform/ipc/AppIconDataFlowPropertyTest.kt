package io.github.magisk317.relay.platform.ipc

import io.github.magisk317.relay.engine.service.DispatchPayloadContext
import io.github.magisk317.relay.testing.relaxedIntent
import io.github.magisk317.relay.testing.stubStringExtra
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Property 7: appIcon 数据流透传保持
 *
 * Validates: Requirements 3.2, 3.3, 4.3
 *
 * Verifies that the appIcon value is preserved through the complete data flow pipeline:
 * ForwardBroadcastPayload → RelayEvent → MsgInfo
 * and the toIntent() → fromIntent() round-trip.
 */
class AppIconDataFlowPropertyTest : FunSpec({

    // Generator for random Base64-like strings (alphanumeric + /+=)
    val base64Chars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('+', '/', '=')
    val base64Arb: Arb<String> = arbitrary { rs ->
        val length = Arb.int(0..200).bind()
        String(CharArray(length) { base64Chars[rs.random.nextInt(base64Chars.size)] })
    }

    test("Property 7: appIcon preserved through ForwardBroadcastPayload to RelayEvent to MsgInfo pipeline") {
        /**
         * **Validates: Requirements 3.2, 3.3, 4.3**
         *
         * For any Base64 string as appIcon, from ForwardBroadcastPayload → RelayEvent → MsgInfo
         * the appIcon value should remain unchanged.
         */
        checkAll(50, base64Arb) { appIconValue ->
            val payload = ForwardBroadcastPayload(
                sender = "Test",
                body = "test body",
                date = 1000L,
                company = "TestApp",
                packageName = "com.test.app",
                msgType = ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY,
                forwardSource = ForwardBroadcastContract.SOURCE_NMS_HOOK,
                eventId = "test_event",
                appIcon = appIconValue,
            )

            // Step 1: ForwardBroadcastPayload → RelayEvent
            val relayEvent = payload.toRelayEvent()
            relayEvent.appIcon shouldBe appIconValue

            // Step 2: RelayEvent → MsgInfo (via DispatchPayloadContext)
            val context = DispatchPayloadContext.from(relayEvent)
            val msgInfo = context.toMsgInfo(relayEvent)
            msgInfo.appIcon shouldBe appIconValue
        }
    }

    test("Property 7: appIcon preserved through ForwardBroadcastPayload toIntent/fromIntent round-trip") {
        /**
         * **Validates: Requirements 3.2, 3.3**
         *
         * For any Base64 string as appIcon, ForwardBroadcastPayload.fromIntent() should
         * correctly parse the appIcon field written by populatePayload, preserving the value
         * through serialization/deserialization.
         */
        checkAll(50, base64Arb) { appIconValue ->
            // Simulate the Intent populated by populatePayload (same as toIntent() does internally)
            val intent = relaxedIntent()
            // populatePayload only writes appIcon if non-empty; fromIntent reads with .orEmpty()
            intent.stubStringExtra(
                ForwardBroadcastContract.EXTRA_APP_ICON,
                appIconValue.ifEmpty { null },
            )

            // Stub remaining required fields for fromIntent to parse correctly
            intent.stubStringExtra(ForwardBroadcastContract.EXTRA_SENDER, "Test")
            intent.stubStringExtra(ForwardBroadcastContract.EXTRA_BODY, "body")
            intent.stubStringExtra(ForwardBroadcastContract.EXTRA_COMPANY, "App")
            intent.stubStringExtra(ForwardBroadcastContract.EXTRA_SMS_CODE, null)
            intent.stubStringExtra(ForwardBroadcastContract.EXTRA_PACKAGE_NAME, "com.test")
            intent.stubStringExtra(ForwardBroadcastContract.EXTRA_NOTIFY_CHANNEL_ID, "ch")
            intent.stubStringExtra(
                ForwardBroadcastContract.EXTRA_MSG_TYPE,
                ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY,
            )
            intent.stubStringExtra(
                ForwardBroadcastContract.EXTRA_FORWARD_SOURCE,
                ForwardBroadcastContract.SOURCE_NMS_HOOK,
            )
            intent.stubStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, "evt")
            intent.stubStringExtra(ForwardBroadcastContract.EXTRA_CALL_STAGE, "")

            val restored = ForwardBroadcastPayload.fromIntent(intent)

            // appIcon should be preserved: non-empty values unchanged, null → ""
            restored.appIcon shouldBe appIconValue
        }
    }
})
