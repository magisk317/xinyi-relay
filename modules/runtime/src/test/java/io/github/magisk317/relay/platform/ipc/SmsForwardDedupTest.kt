package io.github.magisk317.relay.platform.ipc

import io.github.magisk317.smscode.domain.utils.RecentEventDeduplicator
import io.github.magisk317.smscode.domain.utils.SmsForwardDedupKeyFactory
import io.github.magisk317.smscode.domain.utils.SmsForwardDedupSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsForwardDedupTest {

    @Test
    fun build_prefersEventIdWhenPresent() {
        val key = SmsForwardDedupKeyFactory.build(
            SmsForwardDedupSpec(
                eventId = "sms_abc123",
                sender = "1068",
                body = "code 123456",
                timestamp = 1_700_000_000_000L,
            ),
        )

        assertEquals("event:sms_abc123", key)
    }

    @Test
    fun build_fallsBackToSmsFingerprintWhenEventIdMissing() {
        val key = SmsForwardDedupKeyFactory.build(
            SmsForwardDedupSpec(
                sender = "1068",
                body = "code 123456",
                timestamp = 1_700_000_000_000L,
                msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
                source = ForwardBroadcastContract.SOURCE_SMS_HOOK,
                simSlot = 1,
                subId = 2,
            ),
        )

        assertTrue(key.startsWith("sms:sms|sms_hook|1068|code 123456|1700000000000|1|2"))
    }

    @Test
    fun shouldDrop_blocksOnlyWithinWindow() {
        val deduplicator = RecentEventDeduplicator(windowMs = 10_000L)
        val key = "event:sms_abc123"

        assertFalse(deduplicator.shouldDrop(key, now = 1_000L))
        assertTrue(deduplicator.shouldDrop(key, now = 5_000L))
        assertFalse(deduplicator.shouldDrop(key, now = 16_000L))
    }
}
