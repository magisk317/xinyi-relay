package com.github.magisk317.smscode.forwarder.entity

import java.io.Serializable
import java.util.Date

@Suppress("unused")
data class MsgInfo(
    var type: String = "sms",
    var from: String,
    var content: String,
    var date: Date,
    var simInfo: String,
    var simSlot: Int = -1, //卡槽id：-1=获取失败、0=卡槽1、1=卡槽2
    var subId: Int = 0, //卡槽主键
    var callType: Int = 0, //通话类型：1.来电挂机 2.去电挂机 3.未接来电 4.来电提醒 5.来电接通 6.去电拨出
    var uid: Int = 0, //APP通知的UID
    var packageName: String = "",
    var notifyChannelId: String = "",
    var appName: String = "",
    var title: String = "",
    var message: String = "",
    var contactName: String = "",
    var phoneArea: String = "",
) : Serializable {
    // The methods for evaluating templates (like replaceTag, getContentForSend, etc.)
    // have been temporarily removed to decouple from SmsForwarder's heavy utility classes.
    // They will be re-implemented if necessary when building the full notification engine in SmsCode.

    override fun toString(): String {
        return "MsgInfo(from='$from', content='$content', date=$date, simInfo='$simInfo', uid=$uid, type='$type', packageName='$packageName', notifyChannelId='$notifyChannelId')"
    }
}
