package io.github.magisk317.relay.platform.sender.config

import java.io.Serializable

data class FeishuSetting(
    var webhook: String = "",
    val secret: String = "",
    val msgType: String = "interactive",
    val titleTemplate: String = "",
    val messageCard: String = "", //自定义消息卡片
) : Serializable {

}
