package io.github.magisk317.relay.ui.sender

import android.content.Context
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.core.R
import io.github.magisk317.relay.mobilefeature.common.BuildConfig

fun getSenderTypeName(context: Context, type: Int): String {
    return when (type) {
        SenderType.DINGTALK_GROUP_ROBOT -> context.getString(R.string.sender_type_dingtalk_group_robot)
        SenderType.DINGTALK_INNER_ROBOT -> context.getString(R.string.sender_type_dingtalk_inner_robot)
        SenderType.EMAIL -> context.getString(R.string.sender_type_email)
        SenderType.BARK -> context.getString(R.string.sender_type_bark)
        SenderType.WEBHOOK -> context.getString(R.string.sender_type_webhook)
        SenderType.WEWORK_ROBOT -> context.getString(R.string.sender_type_wework_robot)
        SenderType.WEWORK_AGENT -> context.getString(R.string.sender_type_wework_agent)
        SenderType.SERVERCHAN -> context.getString(R.string.sender_type_serverchan)
        SenderType.TELEGRAM -> context.getString(R.string.sender_type_telegram)
        SenderType.MATRIX -> context.getString(R.string.sender_type_matrix)
        SenderType.FEISHU -> context.getString(R.string.sender_type_feishu)
        SenderType.FEISHU_APP -> context.getString(R.string.sender_type_feishu_app)
        SenderType.PUSHPLUS -> context.getString(R.string.sender_type_pushplus)
        SenderType.GOTIFY -> context.getString(R.string.sender_type_gotify)
        SenderType.NTFY -> context.getString(R.string.sender_type_ntfy)
        SenderType.PUSHDEER -> context.getString(R.string.sender_type_pushdeer)
        SenderType.YUNHU -> context.getString(R.string.sender_type_yunhu)
        SenderType.URL_SCHEME -> context.getString(R.string.sender_type_url_scheme)
        SenderType.SOCKET -> context.getString(R.string.sender_type_socket)
        SenderType.SMS -> if (BuildConfig.ENABLE_SMS_CHANNEL) {
            context.getString(R.string.sender_type_sms)
        } else {
            context.getString(R.string.sender_type_sms_unavailable)
        }
        else -> context.getString(R.string.unknown)
    }
}
