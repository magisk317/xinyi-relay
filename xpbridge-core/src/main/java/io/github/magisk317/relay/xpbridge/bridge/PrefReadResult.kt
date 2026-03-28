package io.github.magisk317.relay.xpbridge.bridge

data class PrefReadResult<T>(
    val value: T,
    val source: String,
)
