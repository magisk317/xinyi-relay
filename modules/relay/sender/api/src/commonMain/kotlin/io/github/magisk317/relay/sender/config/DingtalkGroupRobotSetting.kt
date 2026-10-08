package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable


@KotlinSerializable
data class DingtalkGroupRobotSetting(
    var token: String = "",
    var secret: String = "",
    var atAll: Boolean = false,
    var atMobiles: String = "",
    var atDingtalkIds: String = "",
    var msgtype: String = "text",
    val titleTemplate: String = "",
) {

}
