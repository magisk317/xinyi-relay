package io.github.magisk317.relay.xp.hook.code

import io.github.magisk317.relay.common.utils.SmsBlacklistUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsHandlerDispatchDecisionTest {

    @Test
    fun evaluate_prioritizesBlacklistBlock() {
        val decision = SmsHandlerDispatchDecision.evaluate(
            blacklistResult = SmsBlacklistUtils.MatchResult(
                matched = true,
                actionDelete = true,
                actionBlock = true,
            ),
            smsMsgAvailable = true,
            parseResult = ParseResult().apply { isBlockSms = false },
        )

        assertFalse(decision.shouldDeleteByBlacklist)
        assertEquals(SmsHandlerDispatchDecision.BlockReason.BLACKLIST, decision.blockReason)
        assertFalse(decision.shouldAllowSystemPersist)
    }

    @Test
    fun evaluate_schedulesBlacklistDeleteWithoutBlocking() {
        val decision = SmsHandlerDispatchDecision.evaluate(
            blacklistResult = SmsBlacklistUtils.MatchResult(
                matched = true,
                actionDelete = true,
                actionBlock = false,
            ),
            smsMsgAvailable = true,
            parseResult = null,
        )

        assertTrue(decision.shouldDeleteByBlacklist)
        assertNull(decision.blockReason)
        assertFalse(decision.shouldAllowSystemPersist)
    }

    @Test
    fun evaluate_prefBlockWinsWhenBlacklistDoesNotBlock() {
        val decision = SmsHandlerDispatchDecision.evaluate(
            blacklistResult = SmsBlacklistUtils.MatchResult(
                matched = true,
                actionDelete = false,
                actionBlock = false,
            ),
            smsMsgAvailable = true,
            parseResult = ParseResult().apply { isBlockSms = true },
        )

        assertFalse(decision.shouldDeleteByBlacklist)
        assertEquals(SmsHandlerDispatchDecision.BlockReason.PREF_BLOCK, decision.blockReason)
        assertFalse(decision.shouldAllowSystemPersist)
    }

    @Test
    fun evaluate_allowsSystemPersistWhenParseSucceedsWithoutBlock() {
        val decision = SmsHandlerDispatchDecision.evaluate(
            blacklistResult = SmsBlacklistUtils.MatchResult(matched = false),
            smsMsgAvailable = false,
            parseResult = ParseResult().apply { isBlockSms = false },
        )

        assertFalse(decision.shouldDeleteByBlacklist)
        assertNull(decision.blockReason)
        assertTrue(decision.shouldAllowSystemPersist)
    }
}
