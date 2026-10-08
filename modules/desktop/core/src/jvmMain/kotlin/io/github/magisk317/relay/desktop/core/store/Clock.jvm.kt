package io.github.magisk317.relay.desktop.core.store

import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

actual fun sha256Hex(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

actual fun randomUuid(): String = UUID.randomUUID().toString()

/**
 * JVM clock. `ISO_INSTANT` renders the same RFC 3339 UTC shape as the Rust
 * `chrono::Utc::now().to_rfc3339()` output used by the legacy store.
 */
class DesktopClock : Clock {

    override fun nowRfc3339(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun toRfc3339(millis: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))
}
