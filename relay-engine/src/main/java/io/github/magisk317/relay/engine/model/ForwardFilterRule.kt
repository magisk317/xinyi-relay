package io.github.magisk317.relay.engine.model

import kotlinx.serialization.Serializable

@Serializable
data class ForwardFilterRule(
    val id: Long = 0L,
    val msgType: String,
    val scopeType: String,
    val scopeKey: String = "",
    val senderId: Long = 0L,
    val policy: String,
    val matchMode: String,
    val pattern: String,
    val enabled: Int = 1,
    val updateTime: Long = 0L,
)
