package com.github.magisk317.smscode.forwarder.entity.setting

import java.io.Serializable

@Suppress("SENSELESS_COMPARISON")
data class FeishuAppSetting(
    var appId: String = "",
    val appSecret: String = "",
    val receiveId: String = "",
    val msgType: String = "interactive",
    val titleTemplate: String = "",
    val receiveIdType: String = "user_id",
    val messageCard: String = "", //自定义消息卡片
) : Serializable {

}
