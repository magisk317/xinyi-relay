package com.github.magisk317.smscode.forwarder.entity.setting

import java.io.Serializable

data class WeworkRobotSetting(
    var webHook: String = "",
    val msgType: String = "text",
    var atAll: Boolean = false,
    var atUserIds: String = "",
    var atMobiles: String = "",
) : Serializable {

}
