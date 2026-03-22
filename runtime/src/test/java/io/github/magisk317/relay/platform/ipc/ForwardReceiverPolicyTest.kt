package io.github.magisk317.relay.platform.ipc

import io.github.magisk317.relay.common.constant.MessageType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForwardReceiverPolicyTest {

    @Test
    fun shouldAllowSystemTokenBypass_allowsSystemNmsAndLegacyNullUid() {
        assertTrue(
            ForwardReceiverPolicy.shouldAllowSystemTokenBypass(
                msgType = ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY,
                forwardSource = ForwardBroadcastContract.SOURCE_NMS_HOOK,
                sentFromUid = ForwardReceiverPolicy.SYSTEM_UID,
                sdkInt = 34,
            ),
        )
        assertTrue(
            ForwardReceiverPolicy.shouldAllowSystemTokenBypass(
                msgType = ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY,
                forwardSource = ForwardBroadcastContract.SOURCE_NMS_HOOK,
                sentFromUid = null,
                sdkInt = 33,
            ),
        )
        assertFalse(
            ForwardReceiverPolicy.shouldAllowSystemTokenBypass(
                msgType = ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY,
                forwardSource = ForwardBroadcastContract.SOURCE_NMS_HOOK,
                sentFromUid = 20000,
                sdkInt = 34,
            ),
        )
    }

    @Test
    fun shouldAllowSmsHookTokenBypass_matchesSmsHookRules() {
        assertTrue(
            ForwardReceiverPolicy.shouldAllowSmsHookTokenBypass(
                sentFromUid = ForwardReceiverPolicy.SYSTEM_UID,
                sdkInt = 34,
            ),
        )
        assertTrue(
            ForwardReceiverPolicy.shouldAllowSmsHookTokenBypass(
                sentFromUid = ForwardReceiverPolicy.PHONE_UID,
                sdkInt = 34,
            ),
        )
        assertTrue(
            ForwardReceiverPolicy.shouldAllowSmsHookTokenBypass(
                sentFromUid = null,
                sdkInt = 33,
            ),
        )
        assertFalse(
            ForwardReceiverPolicy.shouldAllowSmsHookTokenBypass(
                sentFromUid = 20000,
                sdkInt = 34,
            ),
        )
    }

    @Test
    fun resolveSimSlot_prefersSubIdResolverThenNormalizesLegacySlotValues() {
        assertEquals(
            0,
            ForwardReceiverPolicy.resolveSimSlot(rawSlot = 1, subId = 10) { subId ->
                if (subId == 10) 0 else -1
            },
        )
        assertEquals(
            1,
            ForwardReceiverPolicy.resolveSimSlot(rawSlot = 2, subId = 0) { -1 },
        )
        assertEquals(
            -1,
            ForwardReceiverPolicy.resolveSimSlot(rawSlot = 9, subId = 0) { -1 },
        )
    }

    @Test
    fun resolveRelayMessageType_mapsNotifyAndSmsVariants() {
        assertEquals(
            MessageType.APP_NOTIFY,
            ForwardReceiverPolicy.resolveRelayMessageType(ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY, null),
        )
        assertEquals(
            MessageType.CALL_NOTIFY,
            ForwardReceiverPolicy.resolveRelayMessageType(ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY, null),
        )
        assertEquals(
            MessageType.SMS_CODE,
            ForwardReceiverPolicy.resolveRelayMessageType(ForwardBroadcastContract.MSG_TYPE_SMS, "123456"),
        )
        assertEquals(
            MessageType.SMS_PLAIN,
            ForwardReceiverPolicy.resolveRelayMessageType(ForwardBroadcastContract.MSG_TYPE_SMS, ""),
        )
    }

    @Test
    fun shouldDropDuplicateNotify_usesWindowedDedupCache() {
        val recentNotify = linkedMapOf<String, Long>()

        assertFalse(
            ForwardReceiverPolicy.shouldDropDuplicateNotify(
                msgType = ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY,
                packageName = "com.example",
                sender = "Alice",
                body = "Ping",
                notifyChannelId = "main",
                recentNotify = recentNotify,
                nowMs = 1_000L,
            ),
        )
        assertTrue(
            ForwardReceiverPolicy.shouldDropDuplicateNotify(
                msgType = ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY,
                packageName = "com.example",
                sender = "Alice",
                body = "Ping",
                notifyChannelId = "main",
                recentNotify = recentNotify,
                nowMs = 5_000L,
            ),
        )
        assertFalse(
            ForwardReceiverPolicy.shouldDropDuplicateNotify(
                msgType = ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY,
                packageName = "com.example",
                sender = "Alice",
                body = "Ping",
                notifyChannelId = "main",
                recentNotify = recentNotify,
                nowMs = 12_000L,
            ),
        )
    }

    @Test
    fun callNotifyHelpers_handleOngoingAndTelephonySuppression() {
        assertTrue(
            ForwardReceiverPolicy.shouldDropOngoingCallNotify(
                callStage = "ongoing",
                notifyChannelId = "",
                body = "",
            ),
        )
        assertTrue(
            ForwardReceiverPolicy.shouldDropOngoingCallNotify(
                callStage = "",
                notifyChannelId = "phone_ongoing_call",
                body = "",
            ),
        )
        assertFalse(
            ForwardReceiverPolicy.shouldDropOngoingCallNotify(
                callStage = "ended",
                notifyChannelId = "",
                body = "Call ended",
            ),
        )

        val nmsHookSeen = mutableMapOf<String, Long>()
        ForwardReceiverPolicy.markNmsHookSeen("k1", nmsHookSeen, nowMs = 100L)
        assertTrue(ForwardReceiverPolicy.shouldDropTelephonyState("k1", nmsHookSeen, nowMs = 1_000L))
        assertFalse(ForwardReceiverPolicy.shouldDropTelephonyState("k1", nmsHookSeen, nowMs = 40_000L))
    }
}
