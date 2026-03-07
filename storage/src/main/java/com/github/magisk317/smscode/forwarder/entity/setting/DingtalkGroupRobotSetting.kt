package com.github.magisk317.smscode.forwarder.entity.setting

import java.io.Serializable

data class DingtalkGroupRobotSetting(
    var token: String = "",
    var secret: String = "",
    var atAll: Boolean = false,
    var atMobiles: String = "",
    var atDingtalkIds: String = "",
    var msgtype: String = "text",
    val titleTemplate: String = "",
) : Serializable {

}
