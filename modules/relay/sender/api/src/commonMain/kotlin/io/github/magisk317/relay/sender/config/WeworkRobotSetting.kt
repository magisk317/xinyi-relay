package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable


@KotlinSerializable
data class WeworkRobotSetting(
    var webHook: String = "",
    val msgType: String = "text",
    var atAll: Boolean = false,
    var atUserIds: String = "",
    var atMobiles: String = "",
) {

}
