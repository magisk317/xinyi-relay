package io.github.magisk317.relay.xp.hook.code

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.common.utils.SmsBlacklistUtils
import io.github.magisk317.relay.data.db.entity.SmsMsg
import io.github.magisk317.smscode.xposed.utils.XLog
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsDispatchIntentProcessorTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun handle_passesParsedSmsIntoBlacklistAndDecisionPipeline() {
        stubXLog()
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        var matchedSender: String? = null
        var matchedBody: String? = null
        val parseResult = ParseResult().apply { isBlockSms = true }
        val processor = SmsDispatchIntentProcessor(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            incomingSmsParser = {
                SmsMsg(
                    sender = "1068",
                    body = "otp 123456",
                    date = 100L,
                    msgType = SmsMsg.MSG_TYPE_SMS,
                )
            },
            blacklistMatcher = { _, sender, body ->
                matchedSender = sender
                matchedBody = body
                SmsBlacklistUtils.MatchResult(
                    matched = true,
                    matchType = "number",
                    pattern = "1068",
                    actionDelete = true,
                    actionBlock = false,
                )
            },
            codeParser = { _, _, _, _ -> parseResult },
        )

        val outcome = processor.handle(intent, "evt-1")

        assertEquals("1068", matchedSender)
        assertEquals("otp 123456", matchedBody)
        assertEquals("1068", outcome.smsMsg?.sender)
        assertTrue(outcome.blacklistResult.matched)
        assertEquals(parseResult, outcome.parseResult)
        assertTrue(outcome.decision.shouldDeleteByBlacklist)
        assertNull(outcome.decision.blockReason)
    }

    @Test
    fun handle_reportsNullParseResultWhenCodeWorkerMisses() {
        stubXLog()
        val pluginContext = mockk<Context>(relaxed = true)
        val phoneContext = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        val processor = SmsDispatchIntentProcessor(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            incomingSmsParser = { null },
            blacklistMatcher = { _, _, _ -> SmsBlacklistUtils.MatchResult(matched = false) },
            codeParser = { _, _, _, _ -> null },
        )

        val outcome = processor.handle(intent, "evt-2")

        assertNull(outcome.smsMsg)
        assertFalse(outcome.blacklistResult.matched)
        assertNull(outcome.parseResult)
        assertFalse(outcome.decision.shouldDeleteByBlacklist)
        assertNull(outcome.decision.blockReason)
        assertFalse(outcome.decision.shouldAllowSystemPersist)
    }

    private fun stubXLog() {
        mockkObject(XLog)
        every { XLog.w(any(), *anyVararg()) } returns Unit
    }
}
