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

    companion object {
        const val MSG_TYPE_SMS = 0
        const val MSG_TYPE_APP_NOTIFY = 1
        const val MSG_TYPE_CALL_NOTIFY = 2

        const val FORWARD_STATUS_NONE = 0
        const val FORWARD_STATUS_SUCCESS = 1
        const val FORWARD_STATUS_FAILED = 2
        const val FORWARD_STATUS_PARTIAL = 3
        const val FORWARD_STATUS_BLOCKED = 4
    }
}
