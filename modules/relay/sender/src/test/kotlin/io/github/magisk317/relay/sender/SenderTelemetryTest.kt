package io.github.magisk317.relay.sender

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SenderTelemetryTest {
    @Test
    fun trace_returnsBlockResult() = runBlocking {
        val result = SenderTelemetry.trace(senderType = "test", stage = "test_send") {
            "sent"
        }

        assertEquals("sent", result)
    }

    @Test
    fun trace_rethrowsOriginalException() {
        val expected = IllegalStateException("failed")

        val actual = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                SenderTelemetry.trace(senderType = "test", stage = "test_send") {
                    throw expected
                }
            }
        }

        assertSame(expected, actual)
    }
}
