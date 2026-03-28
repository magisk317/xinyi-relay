package io.github.magisk317.relay.xpbridge

data class XpSmsHookDispatchResult(
    val dispatched: Boolean,
    val bypassUsed: Boolean,
    val tokenPresent: Boolean,
)
