package io.github.magisk317.relay.desktop.platform

import io.github.magisk317.xposed.logging.FixedWindowLogLimiter
import io.github.magisk317.xposed.logging.JsonLineEncoder
import io.github.magisk317.xposed.logging.JsonLineField
import io.github.magisk317.xposed.logging.SecretRedactor
import java.time.Instant

/**
 * Desktop-side structured logger built on the shared kit `logging-core`
 * primitives.
 *
 * The desktop track is plain JVM (Compose Desktop), so it consumes the
 * platform-neutral core instead of the Android-facing
 * `:magisk-xposed-kit:logging` XLog stack: [JsonLineEncoder] shapes one JSON
 * object per line, [SecretRedactor] applies the shared credential policy
 * before anything reaches the sink, and [FixedWindowLogLimiter] keeps a
 * reconnect storm from flooding stderr. Every line carries the component tag
 * so desktop captures stay greppable next to Android and backend ones.
 *
 * Writes to stderr as JSONL: the desktop app is a GUI whose diagnostics are
 * collected from the console, and one machine-readable object per line is what
 * keeps those captures greppable. Repeated events are rate-limited per
 * level+key; a summary line marks every [SUMMARY_STRIDE] suppressed repeat so
 * a storm stays visible without turning into one.
 */
object DesktopLog {
    private const val MAX_EVENTS_PER_WINDOW = 5
    private const val WINDOW_MS = 60_000L
    private const val SUMMARY_STRIDE = 50L

    private val limiter = FixedWindowLogLimiter(
        maxEvents = MAX_EVENTS_PER_WINDOW,
        windowMs = WINDOW_MS,
    )

    /** Logs [message] under [key] at warn level. [key] must not be blank. */
    fun warn(key: String, message: String, fields: Map<String, String> = emptyMap()) {
        emit("warn", key, message, null, fields, System.currentTimeMillis())
    }

    /**
     * Logs [message] under [key] at error level, naming the [throwable] class
     * when given. [key] must not be blank.
     */
    fun error(
        key: String,
        message: String,
        throwable: Throwable? = null,
        fields: Map<String, String> = emptyMap(),
    ) {
        emit("error", key, message, throwable, fields, System.currentTimeMillis())
    }

    /**
     * Rate-limiter-aware emit with an injectable clock so tests can drive
     * window boundaries without sleeping. The public [warn]/[error] wrappers
     * pass the wall clock.
     */
    internal fun emit(
        level: String,
        key: String,
        message: String,
        throwable: Throwable?,
        fields: Map<String, String>,
        nowMs: Long,
    ) {
        val decision = limiter.tryAcquire("$level:$key", nowMs)
        if (!decision.allowed) {
            if (decision.suppressedCount % SUMMARY_STRIDE == 0L) {
                writeLine(
                    buildLine(
                        level = level,
                        key = key,
                        message = "suppressed ${decision.suppressedCount} repeats",
                        throwable = null,
                        fields = emptyMap(),
                        nowMs = nowMs,
                    ),
                )
            }
            return
        }
        writeLine(buildLine(level, key, message, throwable, fields, nowMs))
    }

    private fun buildLine(
        level: String,
        key: String,
        message: String,
        throwable: Throwable?,
        fields: Map<String, String>,
        nowMs: Long,
    ): String {
        val encoded = buildList {
            add(JsonLineField.string("ts", Instant.ofEpochMilli(nowMs).toString()))
            add(JsonLineField.string("level", level))
            add(JsonLineField.string("component", "desktop"))
            add(JsonLineField.string("key", key))
            add(JsonLineField.string("msg", SecretRedactor.redact(message)))
            throwable?.let { add(JsonLineField.string("error", it::class.simpleName ?: "Throwable")) }
            fields.forEach { (name, value) ->
                add(JsonLineField.string(name, SecretRedactor.redact(value)))
            }
        }
        return JsonLineEncoder.encode(encoded)
    }

    private fun writeLine(line: String) {
        System.err.println(line)
    }
}
