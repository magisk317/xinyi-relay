package io.github.magisk317.relay.desktop.platform

import io.github.magisk317.xposed.logging.FixedWindowLogLimiter
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.logging.JsonLineEncoder
import io.github.magisk317.xposed.logging.JsonLineField
import io.github.magisk317.xposed.logging.LogEvent
import io.github.magisk317.xposed.logging.LogSink
import java.time.Instant

/**
 * The desktop rendering of XLog events: one JSON object per line on stderr,
 * exactly the capture shape `DesktopLog` produced before the unification.
 * The desktop app is a GUI whose diagnostics are collected from the console,
 * so a machine-readable line stays greppable next to the Android and backend
 * captures.
 *
 * Repeated events are rate-limited per level+key ([FixedWindowLogLimiter]);
 * a summary line marks every [SUMMARY_STRIDE]-th suppressed repeat so a
 * reconnect storm stays visible without turning into one. This sink runs only
 * in diagnostic builds, so messages and fields stay readable within the shared
 * 80-character budget.
 */
class DesktopJsonlSink : LogSink {

    private val limiter = FixedWindowLogLimiter(
        maxEvents = MAX_EVENTS_PER_WINDOW,
        windowMs = WINDOW_MS,
    )

    override fun append(event: LogEvent) {
        val level = event.level.displayName.lowercase()
        val decision = limiter.tryAcquire("$level:${event.tag}", event.timestamp)
        if (!decision.allowed) {
            if (decision.suppressedCount % SUMMARY_STRIDE == 0L) {
                writeLine(
                    buildLine(
                        timestampMs = event.timestamp,
                        level = level,
                        key = event.tag,
                        message = "suppressed ${decision.suppressedCount} repeats",
                        errorSimpleName = null,
                        fields = emptyMap(),
                    ),
                )
            }
            return
        }
        writeLine(
            buildLine(
                timestampMs = event.timestamp,
                level = level,
                key = event.tag,
                message = event.message,
                errorSimpleName = event.throwableText?.let(::throwableSimpleName),
                fields = event.metadata,
            ),
        )
    }

    internal fun buildLine(
        timestampMs: Long,
        level: String,
        key: String,
        message: String,
        errorSimpleName: String?,
        fields: Map<String, String>,
    ): String {
        val encoded = buildList {
            add(JsonLineField.string("ts", Instant.ofEpochMilli(timestampMs).toString()))
            add(JsonLineField.string("level", level))
            add(JsonLineField.string("component", "desktop"))
            add(JsonLineField.string("key", key))
            add(JsonLineField.string("msg", DefaultLogSanitizer.limitForLog(message)))
            errorSimpleName?.let { add(JsonLineField.string("error", it)) }
            fields.forEach { (name, value) ->
                add(JsonLineField.string(name, DefaultLogSanitizer.limitForLog(value)))
            }
        }
        return JsonLineEncoder.encode(encoded)
    }

    private fun writeLine(line: String) {
        System.err.println(line)
    }

    private fun throwableSimpleName(throwableText: String): String =
        throwableText.lineSequence().firstOrNull()?.substringBefore(":")?.trim()
            ?.substringAfterLast(".")?.takeIf { it.isNotEmpty() } ?: "Throwable"

    private companion object {
        const val MAX_EVENTS_PER_WINDOW = 5
        const val WINDOW_MS = 60_000L
        const val SUMMARY_STRIDE = 50L
    }
}
