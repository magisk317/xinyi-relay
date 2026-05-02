package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class BarkSetting(
    //推送地址
    @SerializedName(value = "server", alternate = ["o"])
    var server: String = "",
    //分组名称
    @SerializedName(value = "group", alternate = ["p"])
    val group: String = "",
    //消息图标
    @SerializedName(value = "icon", alternate = ["q"])
    val icon: String = "",
    //消息声音
    @SerializedName(value = "sound", alternate = ["r"])
    val sound: String = "",
    //消息角标
    @SerializedName(value = "badge", alternate = ["s"])
    val badge: String = "",
    //消息链接
    @SerializedName(value = "url", alternate = ["t"])
    val url: String = "",
    //通知级别
    @SerializedName(value = "level", alternate = ["u"])
    val level: String = "active",
    //标题模板
    @SerializedName(value = "title", alternate = ["v"])
    val title: String = "",
    //加密算法
    @SerializedName(value = "transformation", alternate = ["w"])
    val transformation: String = "none",
    //加密密钥
    @SerializedName(value = "key", alternate = ["x"])
    val key: String = "",
    //初始偏移向量
    @SerializedName(value = "iv", alternate = ["y"])
    var iv: String = "",
    //持续提醒
    @SerializedName(value = "call", alternate = ["z"])
    val call: String = "",
    //自动复制模板
    @SerializedName(value = "autoCopy", alternate = ["A"])
    val autoCopy: String = "",
) : Serializable
