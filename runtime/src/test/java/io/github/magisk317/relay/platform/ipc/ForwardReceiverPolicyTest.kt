package io.github.magisk317.relay.platform.ipc

import io.github.magisk317.relay.contract.constant.MessageType
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
    fun shouldAllowCompatTokenBypass_requiresUninitializedExpectedToken() {
        assertTrue(
            ForwardReceiverPolicy.shouldAllowCompatTokenBypass(
                expectedToken = "",
                msgType = ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY,
                forwardSource = ForwardBroadcastContract.SOURCE_NMS_HOOK,
                sentFromUid = null,
                sdkInt = 33,
            ),
        )
        assertFalse(
            ForwardReceiverPolicy.shouldAllowCompatTokenBypass(
                expectedToken = "initialized",
                msgType = ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY,
                forwardSource = ForwardBroadcastContract.SOURCE_NMS_HOOK,
                sentFromUid = null,
                sdkInt = 33,
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
        assertTrue(
            ForwardReceiverPolicy.shouldAllowSmsHookTokenBypass(
                sentFromUid = -1,
                sdkInt = 34,
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
    fun shouldDropDuplicateForward_deduplicatesReclassifiedNmsSms() {
        val recentNotify = linkedMapOf<String, Long>()

        assertFalse(
            ForwardReceiverPolicy.shouldDropDuplicateForward(
                msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
                forwardSource = ForwardBroadcastContract.SOURCE_NMS_HOOK,
                packageName = "com.android.messaging",
                sender = "豆包",
                body = "验证码 744474",
                notifyChannelId = "messages",
                smsCode = "744474",
                recentNotify = recentNotify,
                nowMs = 1_000L,
            ),
        )
        assertTrue(
            ForwardReceiverPolicy.shouldDropDuplicateForward(
                msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
                forwardSource = ForwardBroadcastContract.SOURCE_NMS_HOOK,
                packageName = "com.android.messaging",
                sender = "豆包",
                body = "验证码 744474，5分钟内有效",
                notifyChannelId = "messages",
                smsCode = "744474",
                recentNotify = recentNotify,
                nowMs = 5_000L,
            ),
        )
        assertFalse(
            ForwardReceiverPolicy.shouldDropDuplicateForward(
                msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
                forwardSource = ForwardBroadcastContract.SOURCE_NMS_HOOK,
                packageName = "com.android.messaging",
                sender = "豆包",
                body = "验证码 744474",
                notifyChannelId = "messages",
                smsCode = "744474",
                recentNotify = recentNotify,
                nowMs = 12_000L,
            ),
        )
    }

    @Test
    fun shouldDropDuplicateForward_deduplicatesAcrossSourcesWhenSmsCodeMatches() {
        val recentNotify = linkedMapOf<String, Long>()

        assertFalse(
            ForwardReceiverPolicy.shouldDropDuplicateForward(
                msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
                forwardSource = ForwardBroadcastContract.SOURCE_NMS_HOOK,
                packageName = "com.android.messaging",
                sender = "豆包",
                body = "验证码 744474，5分钟内有效",
                notifyChannelId = "messages",
                smsCode = "744474",
                recentNotify = recentNotify,
                nowMs = 1_000L,
            ),
        )
        assertTrue(
            ForwardReceiverPolicy.shouldDropDuplicateForward(
                msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
                forwardSource = ForwardBroadcastContract.SOURCE_SMS_HOOK,
                packageName = "com.android.messaging",
                sender = "豆包",
                body = "您的验证码是 744474",
                notifyChannelId = "",
                smsCode = "744474",
                recentNotify = recentNotify,
                nowMs = 5_000L,
            ),
        )
    }

    @Test
    fun shouldDropDuplicateForward_doesNotUseNotifyCacheForSmsHookSms() {
        val recentNotify = linkedMapOf<String, Long>()

        assertFalse(
            ForwardReceiverPolicy.shouldDropDuplicateForward(
                msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
                forwardSource = ForwardBroadcastContract.SOURCE_SMS_HOOK,
                packageName = "com.android.messaging",
                sender = "1068",
                body = "验证码 123456",
                notifyChannelId = "messages",
                smsCode = "123456",
                recentNotify = recentNotify,
                nowMs = 1_000L,
            ),
        )
        assertFalse(
            ForwardReceiverPolicy.shouldDropDuplicateForward(
                msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
                forwardSource = ForwardBroadcastContract.SOURCE_SMS_HOOK,
                packageName = "com.android.messaging",
                sender = "1068",
                body = "验证码 123456",
                notifyChannelId = "messages",
                smsCode = "654321",
                recentNotify = recentNotify,
                nowMs = 5_000L,
            ),
        )
    }

    @Test
    fun successfulSmsHookSuppression_blocksDelayedNmsFallbackWithinWindow() {
        val recentSuccessfulSmsHook = linkedMapOf<String, Long>()

        ForwardReceiverPolicy.markSuccessfulSmsHookDispatch(
            smsCode = "230244",
            company = "潇湘一卡通",
            sender = "1068",
            body = "【潇湘一卡通】验证码 230244，5分钟内有效",
            recentSuccessfulSmsHook = recentSuccessfulSmsHook,
            nowMs = 1_000L,
        )

        assertTrue(
            ForwardReceiverPolicy.shouldSuppressReclassifiedNmsSms(
                smsCode = "230244",
                company = "潇湘一卡通",
                sender = "短信",
                body = "[2条]...验证码 230244，5分钟内有效",
                packageName = "com.android.mms",
                recentSuccessfulSmsHook = recentSuccessfulSmsHook,
                nowMs = 31_000L,
            ),
        )
    }

    @Test
    fun successfulSmsHookSuppression_expiresAfterWindow() {
        val recentSuccessfulSmsHook = linkedMapOf<String, Long>()

        ForwardReceiverPolicy.markSuccessfulSmsHookDispatch(
            smsCode = "230244",
            company = "潇湘一卡通",
            sender = "1068",
            body = "【潇湘一卡通】验证码 230244，5分钟内有效",
            recentSuccessfulSmsHook = recentSuccessfulSmsHook,
            nowMs = 1_000L,
        )

        assertFalse(
            ForwardReceiverPolicy.shouldSuppressReclassifiedNmsSms(
                smsCode = "230244",
                company = "潇湘一卡通",
                sender = "短信",
                body = "[2条]...验证码 230244，5分钟内有效",
                packageName = "com.android.mms",
                recentSuccessfulSmsHook = recentSuccessfulSmsHook,
                nowMs = 130_000L,
            ),
        )
    }

    @Test
    fun forwardedSmsHookSuppression_blocksTelephonyNmsAppNotifyCopyWithinWindow() {
        val recentForwardedSmsHook = linkedMapOf<String, Long>()

        ForwardReceiverPolicy.markForwardedSmsHookDispatch(
            smsCode = null,
            company = "",
            sender = "10687565251201",
            body = "【招商银行】您尾号1234账户收入88.88元",
            recentForwardedSmsHook = recentForwardedSmsHook,
            nowMs = 1_000L,
        )

        assertTrue(
            ForwardReceiverPolicy.shouldSuppressTelephonyNmsCopyAfterSmsHook(
                smsCode = null,
                company = "招商银行",
                sender = "招商银行",
                body = "[2条]...您尾号1234账户收入88.88元",
                packageName = "com.android.mms",
                recentForwardedSmsHook = recentForwardedSmsHook,
                nowMs = 5_000L,
            ),
        )
    }

    @Test
    fun forwardedSmsHookSuppression_ignoresNonTelephonyPackages() {
        val recentForwardedSmsHook = linkedMapOf<String, Long>()

        ForwardReceiverPolicy.markForwardedSmsHookDispatch(
            smsCode = null,
            company = "",
            sender = "10687565251201",
            body = "【招商银行】您尾号1234账户收入88.88元",
            recentForwardedSmsHook = recentForwardedSmsHook,
            nowMs = 1_000L,
        )

        assertFalse(
            ForwardReceiverPolicy.shouldSuppressTelephonyNmsCopyAfterSmsHook(
                smsCode = null,
                company = "招商银行",
                sender = "招商银行",
                body = "[2条]...您尾号1234账户收入88.88元",
                packageName = "com.eg.android.AlipayGphone",
                recentForwardedSmsHook = recentForwardedSmsHook,
                nowMs = 5_000L,
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
