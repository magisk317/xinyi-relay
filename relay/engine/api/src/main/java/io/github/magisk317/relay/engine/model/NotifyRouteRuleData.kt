package io.github.magisk317.relay.engine.model

interface NotifyRouteRuleData {
    val id: Long
    val scope: Int
    val packageName: String
    val senderId: Long
    val updateTime: Long
}
