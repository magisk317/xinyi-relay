package io.github.magisk317.relay.engine.model

interface ReadRecordData {
    val id: Long
    val sender: String?
    val body: String?
    val date: Long
    val processedTime: Long
    val company: String?
    val smsCode: String?
    val packageName: String?
    val notifyChannelId: String
    val simSlot: Int
    val subId: Int
    val contactName: String
    val phoneArea: String
    val forwardStatus: Int
    val forwardTarget: String?
    val forwardMessage: String?
    val forwardTime: Long
    val msgType: Int
    val callType: Int
    val sessionKey: String
}
