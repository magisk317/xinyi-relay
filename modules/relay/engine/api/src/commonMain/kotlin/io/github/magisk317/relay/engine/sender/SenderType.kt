package io.github.magisk317.relay.engine.sender

object SenderType {
    const val DINGTALK_GROUP_ROBOT = 0
    const val EMAIL = 1
    const val BARK = 2
    const val WEBHOOK = 3
    const val WEWORK_ROBOT = 4
    const val WEWORK_AGENT = 5
    const val SERVERCHAN = 6
    const val TELEGRAM = 7
    const val SMS = 8
    const val FEISHU = 9
    const val PUSHPLUS = 10
    const val GOTIFY = 11
    const val DINGTALK_INNER_ROBOT = 12
    const val FEISHU_APP = 13
    const val URL_SCHEME = 14
    const val SOCKET = 15
    const val NTFY = 16
    const val YUNHU = 17
    const val PUSHDEER = 18
    const val MATRIX = 19

    fun defaultName(type: Int): String = when (type) {
        DINGTALK_GROUP_ROBOT -> "钉钉群机器人"
        EMAIL -> "邮件"
        BARK -> "Bark"
        WEBHOOK -> "Webhook"
        WEWORK_ROBOT -> "企微群机器人"
        WEWORK_AGENT -> "企微应用"
        SERVERCHAN -> "Server酱"
        TELEGRAM -> "Telegram机器人"
        SMS -> "短信"
        FEISHU -> "飞书机器人"
        PUSHPLUS -> "PushPlus"
        GOTIFY -> "Gotify"
        NTFY -> "ntfy"
        DINGTALK_INNER_ROBOT -> "钉钉内部机器人"
        FEISHU_APP -> "飞书应用"
        URL_SCHEME -> "URL Scheme"
        SOCKET -> "Socket"
        YUNHU -> "云湖"
        PUSHDEER -> "PushDeer"
        MATRIX -> "Matrix"
        else -> "未知通道$type"
    }

    fun displayName(type: Int, configuredName: String? = null): String {
        val name = configuredName.orEmpty().trim()
        return if (name.isGeneratedFallbackFor(type)) defaultName(type) else name
    }

    private fun String.isGeneratedFallbackFor(type: Int): Boolean {
        return isBlank() || this == type.toString() || this == "通道$type"
    }
}
