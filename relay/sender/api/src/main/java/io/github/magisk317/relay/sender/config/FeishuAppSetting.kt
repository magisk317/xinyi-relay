package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable
import java.io.Serializable

@Suppress("SENSELESS_COMPARISON")

@KotlinSerializable
data class FeishuAppSetting(
    var authType: String = "app_id",
    var appId: String = "",
    val appSecret: String = "",
    val botToken: String = "",
    val receiveId: String = "",
    val msgType: String = "interactive",
    val titleTemplate: String = "",
    val receiveIdType: String = "user_id",
    val messageCard: String = "", //自定义消息卡片
) : Serializable {

}
