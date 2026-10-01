package io.github.magisk317.relay.engine.service

interface RecordRepository {
    suspend fun insertDispatchLog(log: DispatchLogData): Long
    suspend fun insertSmsMsg(msg: SmsMsgData): Long
}

interface DispatchLogData {
    val recordId: Long?
    val senderId: Long
    val senderType: Int
    val msgType: Int
    val success: Boolean
    val createdAt: Long
}

interface SmsMsgData {
    val sender: String?
    val body: String?
    val date: Long
    val company: String?
    val smsCode: String?
    val packageName: String?
    val notifyChannelId: String
    val simSlot: Int
    val subId: Int
    val contactName: String
    val phoneArea: String
    val msgType: Int
    val callType: Int
    val sessionKey: String
}
