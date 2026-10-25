package io.github.magisk317.relay.desktop.platform

import io.github.magisk317.xposed.logging.LogLevel
import io.github.magisk317.xposed.logging.XLog

/**
 * Desktop-side structured logging entry, now a thin wrapper over the shared
 * kit XLog facade: `warn`/`error` keep their `(key, message, fields)` shape
 * and dispatch as [XLog.structured] calls carrying the fields as event
 * metadata. Rendering — one JSON object per line on stderr, per-key rate
 * limiting, summary lines for suppressed storms — lives in [DesktopJsonlSink],
 * installed once in `Main.kt`; the sink is where the kit's own docs put
 * "concrete paths and retention".
 */
object DesktopLog {

    /** Logs [message] under [key] at warn level. [key] must not be blank. */
    fun warn(key: String, message: String, fields: Map<String, String> = emptyMap()) {
        XLog.structured(LogLevel.WARN, message = message, tag = key, metadata = fields)
    }

    /**
     * Logs [message] under [key] at error level, naming the [throwable] when
     * given. [key] must not be blank.
     */
    fun error(
        key: String,
        message: String,
        throwable: Throwable? = null,
        fields: Map<String, String> = emptyMap(),
    ) {
        XLog.structured(LogLevel.ERROR, message = message, tag = key, throwable = throwable, metadata = fields)
    }
}
