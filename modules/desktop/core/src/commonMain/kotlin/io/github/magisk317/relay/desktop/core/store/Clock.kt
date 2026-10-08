package io.github.magisk317.relay.desktop.core.store

/**
 * Time source for the store.
 *
 * Every timestamp written by the legacy Rust store is RFC 3339 in UTC
 * (`chrono::Utc::now().to_rfc3339()`), so the Kotlin port must stay in the same
 * format and precision, otherwise the `ORDER BY created_at ASC` ordering used by
 * the pending-command queue changes meaning for existing rows.
 */
interface Clock {
    fun nowRfc3339(): String
    fun nowMillis(): Long

    /**
     * Formats an epoch-millis value with the same RFC 3339 UTC shape used by
     * [nowRfc3339]. Needed for expiry columns, which are derived from the
     * current time plus a TTL.
     */
    fun toRfc3339(millis: Long): String
}

/**
 * SHA-256 helper used for bind codes and device tokens.
 *
 * The legacy store persists only digests; the plaintext is returned once and
 * never written to disk.
 */
expect fun sha256Hex(value: String): String

/**
 * Random UUID used for bind codes. Bind codes must be unguessable, so the
 * platform CSPRNG is used directly.
 */
expect fun randomUuid(): String
