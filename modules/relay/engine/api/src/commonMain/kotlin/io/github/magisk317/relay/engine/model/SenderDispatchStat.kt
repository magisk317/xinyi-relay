package io.github.magisk317.relay.engine.model

interface SenderDispatchStat {
    val senderType: Int
    val sent: Long
    val success: Long
    val failed: Long
}
