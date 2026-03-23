package io.github.magisk317.relay.platform.sender.config

import java.io.Serializable

data class WeworkRobotSetting(
    var webHook: String = "",
    val msgType: String = "text",
    var atAll: Boolean = false,
    var atUserIds: String = "",
    var atMobiles: String = "",
) : Serializable {

}
