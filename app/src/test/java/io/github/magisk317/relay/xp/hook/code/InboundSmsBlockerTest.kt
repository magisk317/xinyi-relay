package io.github.magisk317.relay.xp.hook.code

import io.github.magisk317.smscode.xposed.utils.XLog
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InboundSmsBlockerTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun blockInboundSms_restoresIdentityAndCompletesBroadcastOnHappyPath() {
        stubXLog()
        val events = mutableListOf<String>()
        val invoker = object : InboundSmsMethodInvoker("handler") {
            override fun deleteFromRawTable(inboundSmsHandler: Any, smsReceiver: Any, reason: String, eventId: String) {
                events += "delete:$reason:$eventId"
            }

            override fun sendEventBroadcastComplete(inboundSmsHandler: Any, reason: String, eventId: String) {
                events += "complete:$reason:$eventId"
            }
        }
        val blocker = InboundSmsBlocker(
            smsHandlerClassName = "handler",
            methodInvoker = invoker,
            clearCallingIdentity = {
                events += "clear"
                42L
            },
            restoreCallingIdentity = { token ->
                events += "restore:$token"
            },
        )

        blocker.blockInboundSms(Any(), Any(), "pref_block_sms", "evt-1")

        assertEquals(
            listOf("clear", "delete:pref_block_sms:evt-1", "restore:42", "complete:pref_block_sms:evt-1"),
            events,
        )
    }

    @Test
    fun blockInboundSms_stillRestoresIdentityAndContinuesWhenDeleteFails() {
        stubXLog()
        val events = mutableListOf<String>()
        val invoker = object : InboundSmsMethodInvoker("handler") {
            override fun deleteFromRawTable(inboundSmsHandler: Any, smsReceiver: Any, reason: String, eventId: String) {
                events += "delete"
                error("boom")
            }

            override fun sendEventBroadcastComplete(inboundSmsHandler: Any, reason: String, eventId: String) {
                events += "complete"
            }
        }
        val blocker = InboundSmsBlocker(
            smsHandlerClassName = "handler",
            methodInvoker = invoker,
            clearCallingIdentity = {
                events += "clear"
                7L
            },
            restoreCallingIdentity = { token ->
                events += "restore:$token"
            },
        )

        blocker.blockInboundSms(Any(), Any(), "blacklist_block", "evt-2")

        assertEquals(listOf("clear", "delete", "restore:7", "complete"), events)
    }

    private fun stubXLog() {
        mockkObject(XLog)
        every { XLog.w(any(), *anyVararg()) } returns Unit
        every { XLog.e(any(), *anyVararg()) } returns Unit
    }
}
