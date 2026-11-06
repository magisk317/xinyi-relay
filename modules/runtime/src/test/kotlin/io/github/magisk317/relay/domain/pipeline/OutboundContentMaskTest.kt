package io.github.magisk317.relay.domain.pipeline

import io.github.magisk317.forward.engine.model.MsgInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class OutboundContentMaskTest {

    private fun msg(
        from: String = "13812345678",
        content: String = "Your code is 246810, do not share it",
        smsCode: String = "246810",
        contactName: String = "",
        title: String = "",
        message: String = "",
    ): MsgInfo = MsgInfo(
        from = from,
        content = content,
        date = Instant.fromEpochMilliseconds(0),
        simInfo = "SIM1",
        smsCode = smsCode,
        contactName = contactName,
        title = title,
        message = message,
    )

    @Test
    fun `disabled passthrough keeps everything untouched`() {
        val original = msg()
        assertEquals(original, OutboundContentMask.apply(original, enabled = false))
    }

    @Test
    fun `enabled masks sender and keeps the tail visible`() {
        val out = OutboundContentMask.apply(msg(), enabled = true)
        assertEquals("*******5678", out.from)
    }

    @Test
    fun `enabled masks every code occurrence but preserves the body`() {
        val out = OutboundContentMask.apply(
            msg(
                content = "Your code is 246810, do not share it",
                title = "Code 246810 received",
                message = "again 246810",
            ),
            enabled = true,
        )
        assertEquals("Your code is ******, do not share it", out.content)
        assertEquals("Code ****** received", out.title)
        assertEquals("again ******", out.message)
    }

    @Test
    fun `enabled masks the contact name`() {
        val out = OutboundContentMask.apply(msg(contactName = "Alice"), enabled = true)
        assertEquals("*****", out.contactName)
    }

    @Test
    fun `enabled without a parsed code only masks sender fields`() {
        val out = OutboundContentMask.apply(msg(smsCode = ""), enabled = true)
        assertEquals("*******5678", out.from)
        assertEquals("Your code is 246810, do not share it", out.content)
    }

    @Test
    fun `short senders are fully masked`() {
        val out = OutboundContentMask.apply(msg(from = "bank"), enabled = true)
        assertEquals("****", out.from)
    }
}
