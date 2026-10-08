package io.github.magisk317.relay.engine.filter

/**
 * Returns a thread-safe [MutableMap] backed by the platform concurrent hash map
 * (`java.util.concurrent.ConcurrentHashMap` on both JVM targets).
 *
 * An expect/actual *function* is used instead of an expect/actual class: classes
 * are still Beta (KT-61573), while functions are stable. A typealias to the JDK
 * class is also impossible — its covariant `keys` override (`KeySetView`) does
 * not satisfy a `MutableSet<K>` member.
 */
internal expect fun <K, V> newConcurrentMap(): MutableMap<K, V>
