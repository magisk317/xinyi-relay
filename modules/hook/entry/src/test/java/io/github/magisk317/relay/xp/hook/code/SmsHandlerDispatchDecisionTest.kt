package io.github.magisk317.relay.xp.hook.code

import io.github.magisk317.smscode.verification.SmsHandlerDispatchDecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsHandlerDispatchDecisionTest {

    @Test
    fun evaluate_prioritizesBlacklistBlock() {
        val decision = SmsHandlerDispatchDecision.evaluate(
            blacklistMatched = true,
            blacklistActionDelete = true,
            blacklistActionBlock = true,
            smsMsgAvailable = true,
            parseResultBlockSms = false,
        )

        assertFalse(decision.shouldDeleteByBlacklist)
        assertEquals(SmsHandlerDispatchDecision.BlockReason.BLACKLIST, decision.blockReason)
        assertFalse(decision.shouldAllowSystemPersist)
    }

    @Test
    fun evaluate_schedulesBlacklistDeleteWithoutBlocking() {
        val decision = SmsHandlerDispatchDecision.evaluate(
            blacklistMatched = true,
            blacklistActionDelete = true,
            blacklistActionBlock = false,
            smsMsgAvailable = true,
            parseResultBlockSms = null,
        )

        assertTrue(decision.shouldDeleteByBlacklist)
        assertNull(decision.blockReason)
        assertFalse(decision.shouldAllowSystemPersist)
    }

    @Test
    fun evaluate_prefBlockWinsWhenBlacklistDoesNotBlock() {
        val decision = SmsHandlerDispatchDecision.evaluate(
            blacklistMatched = true,
            blacklistActionDelete = false,
            blacklistActionBlock = false,
            smsMsgAvailable = true,
            parseResultBlockSms = true,
        )

        assertFalse(decision.shouldDeleteByBlacklist)
        assertEquals(SmsHandlerDispatchDecision.BlockReason.PREF_BLOCK, decision.blockReason)
        assertFalse(decision.shouldAllowSystemPersist)
    }

    @Test
    fun evaluate_allowsSystemPersistWhenParseSucceedsWithoutBlock() {
        val decision = SmsHandlerDispatchDecision.evaluate(
            blacklistMatched = false,
            blacklistActionDelete = false,
            blacklistActionBlock = false,
            smsMsgAvailable = false,
            parseResultBlockSms = false,
        )

        assertFalse(decision.shouldDeleteByBlacklist)
        assertNull(decision.blockReason)
        assertTrue(decision.shouldAllowSystemPersist)
    }
}
