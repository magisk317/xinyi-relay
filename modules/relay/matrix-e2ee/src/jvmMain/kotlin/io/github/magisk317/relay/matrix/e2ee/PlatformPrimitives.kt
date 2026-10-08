package io.github.magisk317.relay.matrix.e2ee

import java.security.MessageDigest

internal actual fun platformSha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}

internal actual fun <K, V> newConcurrentMap(): MutableMap<K, V> =
    java.util.concurrent.ConcurrentHashMap()
