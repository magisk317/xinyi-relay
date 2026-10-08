package io.github.magisk317.relay.sender

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SenderLoggerTest {

    @AfterEach
    fun tearDown() {
        SenderLogger.resetForTest()
    }

    @Test
    fun senderLoggerSanitizesMessagesBeforeSinkAppend() {
        var captured: CapturedSenderLog? = null
        SenderLogger.install(
            sink = object : SenderLogSink {
                override fun append(priority: Int, tag: String, message: String, force: Boolean, route: String?) {
                    captured = CapturedSenderLog(priority, tag, message, force, route)
                }
            },
        )

        SenderLogger.e("sender", "token=secret123 body=验证码123456", IllegalStateException("boom"))

        assertEquals(6, captured?.priority)
        assertEquals("sender", captured?.tag)
        assertEquals(SenderLogSink.ROUTE_SENDER, captured?.route)
        assertTrue(captured?.force ?: false)
        assertFalse(captured?.message.orEmpty().contains("secret123"))
        assertFalse(captured?.message.orEmpty().contains("验证码123456"))
        assertTrue(captured?.message.orEmpty().contains("java.lang.IllegalStateException: boom"))
        assertTrue(captured?.message.orEmpty().contains("token=***"))
    }

    @Test
    fun senderLoggerDoesNotForceDebugRuntimeStorage() {
        var captured: CapturedSenderLog? = null
        SenderLogger.install(
            sink = object : SenderLogSink {
                override fun append(priority: Int, tag: String, message: String, force: Boolean, route: String?) {
                    captured = CapturedSenderLog(priority, tag, message, force, route)
                }
            },
        )

        SenderLogger.d("sender", "debug body=hello")

        assertEquals(3, captured?.priority)
        assertFalse(captured?.force ?: true)
    }

    private data class CapturedSenderLog(
        val priority: Int,
        val tag: String,
        val message: String,
        val force: Boolean,
        val route: String?,
    )
}
