package io.github.magisk317.relay.xp.bridge

data class PrefReadResult<T>(
    val value: T,
    val source: String,
)
