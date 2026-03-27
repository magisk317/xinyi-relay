package io.github.magisk317.relay.xp.hook.code

import io.github.magisk317.smscode.xposed.hook.telephony.InboundSmsMethodInvoker
import io.github.magisk317.smscode.xposed.utils.XLog
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InboundSmsMethodInvokerTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun deleteFromRawTable_invokesMatchingOverloadWithReceiverFields() {
        stubXLog()
        val handler = FakeDeleteHandler()
        val receiver = FakeReceiver(
            deleteWhere = "address=?",
            deleteWhereArgs = arrayOf("1068"),
        )
        val invoker = InboundSmsMethodInvoker(
            smsHandlerClassName = FakeDeleteHandler::class.java.name,
            fieldReader = { target, field ->
                val receiverTarget = target as FakeReceiver
                when (field) {
                    "mDeleteWhere" -> receiverTarget.deleteWhere
                    "mDeleteWhereArgs" -> receiverTarget.deleteWhereArgs
                    else -> null
                }
            },
            classResolver = { _, _ -> FakeDeleteHandler::class.java },
        )

        invoker.deleteFromRawTable(handler, receiver, "pref_block_sms", "evt-1")

        assertEquals("address=?", handler.deleteWhere)
        assertArrayEquals(arrayOf("1068"), handler.deleteWhereArgs)
        assertEquals(2, handler.markDeleted)
    }

    @Test
    fun sendEventBroadcastComplete_usesSuperclassSendMessageOverload() {
        stubXLog()
        val handler = FakeSendMessageHandler()
        val invoker = InboundSmsMethodInvoker(
            smsHandlerClassName = FakeSendMessageHandler::class.java.name,
            classResolver = { _, _ -> FakeSendMessageHandler::class.java },
        )

        invoker.sendEventBroadcastComplete(handler, "pref_block_sms", "evt-2")

        assertEquals(3, handler.sentWhat)
    }

    private fun stubXLog() {
        mockkObject(XLog)
        every { XLog.w(any(), *anyVararg()) } returns Unit
        every { XLog.d(any(), *anyVararg()) } returns Unit
    }

    data class FakeReceiver(
        val deleteWhere: String,
        val deleteWhereArgs: Array<String>,
    )

    open class FakeDeleteBase

    class FakeDeleteHandler : FakeDeleteBase() {
        var deleteWhere: String? = null
        var deleteWhereArgs: Array<String>? = null
        var markDeleted: Int? = null

        @Suppress("unused")
        fun deleteFromRawTable(where: String?, whereArgs: Array<String>?, markDeleted: Int) {
            deleteWhere = where
            deleteWhereArgs = whereArgs
            this.markDeleted = markDeleted
        }
    }

    open class FakeSendMessageBase {
        var sentWhat: Int? = null

        @Suppress("unused")
        fun sendMessage(what: Int) {
            sentWhat = what
        }
    }

    class FakeSendMessageHandler : FakeSendMessageBase()
}
