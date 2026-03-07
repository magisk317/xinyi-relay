package com.github.magisk317.smscode.forwarder.entity.setting

import java.io.Serializable
import java.net.Proxy

data class DingtalkInnerRobotSetting(
    val agentID: String = "",
    val appKey: String = "",
    val appSecret: String = "",
    val userIds: String = "",
    val msgKey: String = "sampleText",
    val titleTemplate: String = "",
    val proxyType: Proxy.Type = Proxy.Type.DIRECT,
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyAuthenticator: Boolean = false,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
) : Serializable {

}
