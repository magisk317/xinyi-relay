package io.github.magisk317.relay.desktop.platform

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins the desktop JSONL logger contract: one parseable object per line, the
 * shared redactor applied to messages and fields alike, the throwable class
 * named, and the fixed-window limiter holding a flood to
 * [DesktopLog]-configured events per window with a stride summary.
 */
class DesktopLogTest {
    private val captured = ByteArrayOutputStream()
    private val originalErr = System.err

    @BeforeEach
    fun setUp() {
        System.setErr(PrintStream(captured, true))
    }

    @AfterEach
    fun tearDown() {
        System.setErr(originalErr)
    }

    private fun lines(): List<String> =
        captured.toString().lines().filter { it.isNotBlank() }

    @Test
    fun `emits one json object per event with level key and component`() {
        DesktopLog.emit("warn", "test.shape", "hello world", null, emptyMap(), nowMs = 1_700_000_000_000L)

        val emitted = lines()
        assertEquals(1, emitted.size)
        val json = Json.parseToJsonElement(emitted.single()).jsonObject
        assertEquals("warn", json["level"]?.jsonPrimitive?.content)
        assertEquals("desktop", json["component"]?.jsonPrimitive?.content)
        assertEquals("test.shape", json["key"]?.jsonPrimitive?.content)
        assertEquals("hello world", json["msg"]?.jsonPrimitive?.content)
        assertTrue(json["ts"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    @Test
    fun `redacts secrets in message and field values`() {
        DesktopLog.emit(
            "error",
            "test.redact",
            "connect failed with token=supersecret123",
            null,
            mapOf("authorization" to "Bearer abcdef987654"),
            nowMs = 1_700_000_000_000L,
        )

        val line = lines().single()
        assertFalse(line.contains("supersecret123"), "raw token leaked into the log line")
        assertFalse(line.contains("abcdef987654"), "raw bearer leaked into the log line")
        assertTrue(line.contains("***"), "redaction marker missing")
    }

    @Test
    fun `names the throwable class on error events`() {
        DesktopLog.emit(
            "error",
            "test.throwable",
            "died",
            IllegalStateException("boom"),
            emptyMap(),
            nowMs = 1_700_000_000_000L,
        )

        assertEquals("IllegalStateException", lines().single().let { Json.parseToJsonElement(it).jsonObject["error"]?.jsonPrimitive?.content })
    }

    @Test
    fun `rate limits a flood and emits a summary at the suppression stride`() {
        val nowMs = 1_700_000_000_000L
        // Five events fit the window; the next fifty are suppressed, and the
        // fiftieth suppression emits one summary line.
        repeat(55) { DesktopLog.emit("warn", "test.flood", "boom", null, emptyMap(), nowMs) }

        val emitted = lines()
        assertEquals(6, emitted.size, "five events plus one stride summary expected")
        val summary = Json.parseToJsonElement(emitted.last()).jsonObject
        assertEquals("suppressed 50 repeats", summary["msg"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a new window admits events again`() {
        val windowStart = 1_700_000_000_000L
        repeat(5) { DesktopLog.emit("warn", "test.window", "boom", null, emptyMap(), windowStart) }
        // One more inside the window is suppressed; past it the window resets.
        DesktopLog.emit("warn", "test.window", "boom", null, emptyMap(), windowStart + 1_000L)
        DesktopLog.emit("warn", "test.window", "after reset", null, emptyMap(), windowStart + 61_000L)

        val emitted = lines()
        assertEquals(6, emitted.size)
        assertTrue(emitted.last().contains("after reset"))
    }
}
