package io.github.magisk317.relay.desktop.platform

import io.github.magisk317.xposed.logging.LogLevel
import io.github.magisk317.xposed.logging.XLog
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
 * Pins the desktop JSONL contract over the unified XLog pipeline:
 * DesktopLog routes warn/error through XLog with the key as tag and the
 * fields as metadata, and DesktopJsonlSink renders one parseable object per
 * line on stderr with the shared redactor applied, the throwable class
 * named, and the fixed-window limiter holding a flood to five events per
 * level+key window.
 */
class DesktopLogTest {
    private val captured = ByteArrayOutputStream()
    private val originalErr = System.err

    @BeforeEach
    fun setUp() {
        System.setErr(PrintStream(captured, true))
        XLog.resetForTest()
        XLog.configure(
            tag = "xinyi-desktop",
            logLevel = LogLevel.VERBOSE.priority,
            sink = DesktopJsonlSink(),
            printEnabled = false,
        )
    }

    @AfterEach
    fun tearDown() {
        XLog.resetForTest()
        System.setErr(originalErr)
    }

    private fun lines(): List<String> =
        captured.toString().lines().filter { it.isNotBlank() }

    @Test
    fun `emits one json object per event with level key and component`() {
        DesktopLog.warn("test.shape", "hello world")

        val parsed = Json.parseToJsonElement(lines().single()).jsonObject
        assertEquals("warn", parsed["level"]!!.jsonPrimitive.content)
        assertEquals("desktop", parsed["component"]!!.jsonPrimitive.content)
        assertEquals("test.shape", parsed["key"]!!.jsonPrimitive.content)
        assertEquals("hello world", parsed["msg"]!!.jsonPrimitive.content)
    }

    @Test
    fun `carries fields as redacted json members`() {
        DesktopLog.error("test.fields", "upload failed", fields = mapOf("authorization" to "Bearer abc123secret"))

        val parsed = Json.parseToJsonElement(lines().single()).jsonObject
        assertTrue(parsed["authorization"]!!.jsonPrimitive.content.contains("***"))
        assertFalse(parsed["authorization"]!!.jsonPrimitive.content.contains("abc123secret"))
    }

    @Test
    fun `names the throwable class`() {
        DesktopLog.error("test.throwable", "failed", IllegalStateException("boom"))

        val parsed = Json.parseToJsonElement(lines().single()).jsonObject
        assertEquals("IllegalStateException", parsed["error"]!!.jsonPrimitive.content)
    }

    @Test
    fun `limiter holds a same-key flood to the window budget`() {
        repeat(8) { DesktopLog.warn("test.flood", "repeat $it") }

        // Five events pass, the rest are suppressed without hitting the
        // summary stride, so exactly five lines reach stderr.
        assertEquals(5, lines().size)
    }

    @Test
    fun `distinct keys each get their own window budget`() {
        repeat(3) { DesktopLog.warn("test.flood.a", "a $it") }
        repeat(3) { DesktopLog.warn("test.flood.b", "b $it") }

        assertEquals(6, lines().size)
    }
}
