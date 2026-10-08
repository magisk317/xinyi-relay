package io.github.magisk317.relay.engine.filter

internal actual fun <K, V> newConcurrentMap(): MutableMap<K, V> =
    java.util.concurrent.ConcurrentHashMap()
