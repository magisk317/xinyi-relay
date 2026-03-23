package io.github.magisk317.relay.xp

data class XpSmsHookDispatchResult(
    val dispatched: Boolean,
    val bypassUsed: Boolean,
    val tokenPresent: Boolean,
)
