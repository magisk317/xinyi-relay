package io.github.magisk317.relay.contract.xpbridge

data class XpSmsRecord(
    val id: Long = 0,
    val sender: String? = null,
    val body: String? = null,
    val date: Long = 0,
    val processedTime: Long = 0L,
    val company: String? = null,
    val smsCode: String? = null,
    val packageName: String? = null,
    val notifyChannelId: String = "",
    val forwardStatus: Int = FORWARD_STATUS_NONE,
    val forwardTarget: String? = null,
    val forwardMessage: String? = null,
    val forwardTime: Long = 0L,
    val msgType: Int = MSG_TYPE_SMS,
    val callType: Int = 0,
) {
    companion object {
        const val FORWARD_STATUS_NONE = 0
        const val FORWARD_STATUS_SUCCESS = 1
        const val FORWARD_STATUS_FAILED = 2
        const val FORWARD_STATUS_PARTIAL = 3
        const val FORWARD_STATUS_BLOCKED = 4

        const val MSG_TYPE_SMS = 0
        const val MSG_TYPE_APP_NOTIFY = 1
        const val MSG_TYPE_CALL_NOTIFY = 2
    }
}
