package io.github.magisk317.relay.platform.ipc

import io.github.magisk317.relay.contract.xpbridge.XpSmsBlacklistHitRecord
import io.github.magisk317.relay.testing.relaxedIntent
import io.github.magisk317.relay.testing.stubBooleanExtra
import io.github.magisk317.relay.testing.stubLongExtra
import io.github.magisk317.relay.testing.stubStringExtra
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BlacklistHitBroadcastTest {

    @Test
    fun fromIntent_reconstructsHitRecord() {
        val intent = relaxedIntent()
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_MSG_TYPE, ForwardBroadcastContract.MSG_TYPE_BLACKLIST_HIT)
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, "sms_evt_1")
        intent.stubStringExtra("blacklist_hit_source", "sms_forward")
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_SENDER, "10001")
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_BODY, "spam body")
        intent.stubLongExtra(ForwardBroadcastContract.EXTRA_DATE, 1_700_000_000_000L)
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_MATCH_TYPE, "number")
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_PATTERN, "10001")
        intent.stubBooleanExtra(ForwardBroadcastContract.EXTRA_ACTION_DELETE, false)
        intent.stubBooleanExtra(ForwardBroadcastContract.EXTRA_ACTION_BLOCK, true)
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_BLOCK_REASON, "blacklist_block")
        every { intent.getLongExtra(ForwardBroadcastContract.EXTRA_CREATED_AT, any()) } returns 1_700_000_111_000L

        val hit = BlacklistHitBroadcast.fromIntent(intent)

        assertEquals(
            XpSmsBlacklistHitRecord(
                eventId = "sms_evt_1",
                source = "sms_forward",
                sender = "10001",
                body = "spam body",
                smsDate = 1_700_000_000_000L,
                matchType = "number",
                pattern = "10001",
                actionDelete = false,
                actionBlock = true,
                blockReason = "blacklist_block",
                createdAt = 1_700_000_111_000L,
            ),
            hit,
        )
    }

    @Test
    fun fromIntent_returnsNullForNonBlacklistMessage() {
        val intent = relaxedIntent()
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_MSG_TYPE, ForwardBroadcastContract.MSG_TYPE_SMS)

        assertNull(BlacklistHitBroadcast.fromIntent(intent))
    }

    @Test
    fun fromIntent_returnsNullWhenEventIdMissing() {
        val intent = relaxedIntent()
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_MSG_TYPE, ForwardBroadcastContract.MSG_TYPE_BLACKLIST_HIT)
        intent.stubStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, "")
        intent.stubStringExtra("blacklist_hit_source", "sms_forward")

        assertNull(BlacklistHitBroadcast.fromIntent(intent))
    }
}
