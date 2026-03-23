package io.github.magisk317.relay.domain.model

import java.io.Serializable
import java.util.Date

@Suppress("unused")
data class MsgInfo(
    val type: String = "sms",
    val from: String,
    val content: String,
    val date: Date,
    val simInfo: String,
    val simSlot: Int = -1, //卡槽id：-1=获取失败、0=卡槽1、1=卡槽2
    val subId: Int = 0, //卡槽主键
    val callType: Int = 0, //通话类型：1.来电挂机 2.去电挂机 3.未接来电 4.来电提醒 5.来电接通 6.去电拨出
    val uid: Int = 0, //APP通知的UID
    val packageName: String = "",
    val notifyChannelId: String = "",
    val appName: String = "",
    val title: String = "",
    val message: String = "",
    val contactName: String = "",
    val phoneArea: String = "",
) : Serializable {
    override fun toString(): String {
        return "MsgInfo(" +
            "from='$from', content='$content', date=$date, simInfo='$simInfo', uid=$uid, " +
            "type='$type', packageName='$packageName', notifyChannelId='$notifyChannelId'" +
            ")"
    }
}
