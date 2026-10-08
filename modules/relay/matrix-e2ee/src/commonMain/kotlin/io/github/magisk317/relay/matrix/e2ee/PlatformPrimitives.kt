package io.github.magisk317.relay.matrix.e2ee

/**
 * Returns a thread-safe [MutableMap] backed by the platform concurrent hash map
 * (`java.util.concurrent.ConcurrentHashMap` on both JVM targets).
 *
 * An expect/actual *function* is used instead of an expect/actual class: classes
 * are still Beta (KT-61573), while functions are stable.
 */
internal expect fun <K, V> newConcurrentMap(): MutableMap<K, V>

internal expect fun platformSha256Hex(input: String): String
