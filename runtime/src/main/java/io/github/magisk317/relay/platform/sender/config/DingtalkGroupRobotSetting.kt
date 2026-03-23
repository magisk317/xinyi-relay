package io.github.magisk317.relay.platform.sender.config

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
