package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.relay.engine.service.SenderDispatchResult
import io.github.magisk317.relay.engine.service.SenderDispatcher
import io.github.magisk317.relay.sender.config.BarkSetting
import io.github.magisk317.relay.sender.config.DingtalkGroupRobotSetting
import io.github.magisk317.relay.sender.config.DingtalkInnerRobotSetting
import io.github.magisk317.relay.sender.config.EmailSetting
import io.github.magisk317.relay.sender.config.FeishuAppSetting
import io.github.magisk317.relay.sender.config.FeishuSetting
import io.github.magisk317.relay.sender.config.GotifySetting
import io.github.magisk317.relay.sender.config.NtfySetting
import io.github.magisk317.relay.sender.config.PushplusSetting
import io.github.magisk317.relay.sender.config.ServerchanSetting
import io.github.magisk317.relay.sender.config.SmsSetting
import io.github.magisk317.relay.sender.config.SocketSetting
import io.github.magisk317.relay.sender.config.TelegramSetting
import io.github.magisk317.relay.sender.config.UrlSchemeSetting
import io.github.magisk317.relay.sender.config.WebhookSetting
import io.github.magisk317.relay.sender.config.WeworkAgentSetting
import io.github.magisk317.relay.sender.config.WeworkRobotSetting
import io.github.magisk317.relay.sender.config.YunhuSetting
import kotlinx.serialization.SerializationException

class DefaultSenderDispatcher(private val context: Context) : SenderDispatcher {
    override suspend fun dispatchToSender(
        sender: Sender,
        msgInfo: MsgInfo,
        traceId: String?,
    ): SenderDispatchResult {
        val safeSender = SenderSettingSanitizer.sanitizeSenderLenient(sender)
        val senderName = safeSender.name.ifBlank { "通道${safeSender.type}" }
        SLog.d("DefaultSenderDispatcher", "Dispatching to sender: id=${safeSender.id}, type=${safeSender.type}, name=${safeSender.name}")
        try {
            when (safeSender.type) {
                SenderType.DINGTALK_GROUP_ROBOT -> DingtalkGroupRobotUtils.sendMsg(
                    SenderSettingJson.decode(DingtalkGroupRobotSetting.serializer(), safeSender.jsonSetting),
                    msgInfo,
                )
                SenderType.EMAIL -> EmailUtils.sendMsg(
                    SenderSettingJson.decode(EmailSetting.serializer(), safeSender.jsonSetting),
                    msgInfo,
                    traceId,
                )
                SenderType.BARK -> BarkUtils.sendMsg(SenderSettingJson.decode(BarkSetting.serializer(), safeSender.jsonSetting), msgInfo)
                SenderType.WEBHOOK -> WebhookUtils.sendMsg(SenderSettingJson.decode(WebhookSetting.serializer(), safeSender.jsonSetting), msgInfo, traceId)
                SenderType.WEWORK_ROBOT -> WeworkRobotUtils.sendMsg(SenderSettingJson.decode(WeworkRobotSetting.serializer(), safeSender.jsonSetting), msgInfo)
                SenderType.WEWORK_AGENT -> WeworkAgentUtils.sendMsg(SenderSettingJson.decode(WeworkAgentSetting.serializer(), safeSender.jsonSetting), msgInfo)
                SenderType.SERVERCHAN -> ServerchanUtils.sendMsg(SenderSettingJson.decode(ServerchanSetting.serializer(), safeSender.jsonSetting), msgInfo)
                SenderType.PUSHPLUS -> PushplusUtils.sendMsg(SenderSettingJson.decode(PushplusSetting.serializer(), safeSender.jsonSetting), msgInfo)
                SenderType.TELEGRAM -> TelegramUtils.sendMsg(SenderSettingJson.decode(TelegramSetting.serializer(), safeSender.jsonSetting), msgInfo)
                SenderType.SMS -> {
                    if (!BuildConfig.ENABLE_SMS_CHANNEL) {
                        SLog.w("DefaultSenderDispatcher", "SMS sender disabled in current distribution, skipping sender [${safeSender.name}]")
                    } else {
                        SmsUtils.sendMsg(context, SenderSettingJson.decode(SmsSetting.serializer(), safeSender.jsonSetting), msgInfo)
                    }
                }
                SenderType.FEISHU -> FeishuUtils.sendMsg(SenderSettingJson.decode(FeishuSetting.serializer(), safeSender.jsonSetting), msgInfo)
                SenderType.GOTIFY -> GotifyUtils.sendMsg(SenderSettingJson.decode(GotifySetting.serializer(), safeSender.jsonSetting), msgInfo)
                SenderType.NTFY -> NtfyUtils.sendMsg(SenderSettingJson.decode(NtfySetting.serializer(), safeSender.jsonSetting), msgInfo)
                SenderType.DINGTALK_INNER_ROBOT -> DingtalkInnerRobotUtils.sendMsg(
                    SenderSettingJson.decode(DingtalkInnerRobotSetting.serializer(), safeSender.jsonSetting),
                    msgInfo,
                )
                SenderType.FEISHU_APP -> FeishuAppUtils.sendMsg(SenderSettingJson.decode(FeishuAppSetting.serializer(), safeSender.jsonSetting), msgInfo)
                SenderType.URL_SCHEME -> UrlSchemeUtils.sendMsg(context, SenderSettingJson.decode(UrlSchemeSetting.serializer(), safeSender.jsonSetting), msgInfo)
                SenderType.SOCKET -> SocketUtils.sendMsg(SenderSettingJson.decode(SocketSetting.serializer(), safeSender.jsonSetting), msgInfo)
                SenderType.YUNHU -> YunhuUtils.sendMsg(SenderSettingJson.decode(YunhuSetting.serializer(), safeSender.jsonSetting), msgInfo)
                else -> {
                    val message = "Unsupported sender type: ${safeSender.type}"
                    return SenderDispatchResult(safeSender.id, safeSender.type, senderName, false, message)
                }
            }
            return SenderDispatchResult(safeSender.id, safeSender.type, senderName, true, "OK")
        } catch (e: SerializationException) {
            return SenderDispatchResult(safeSender.id, safeSender.type, senderName, false, "配置解析失败: ${e.message ?: "SerializationException"}")
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val errorSummary = "${e.javaClass.simpleName}: ${e.message ?: "<empty>"}"
            return SenderDispatchResult(safeSender.id, safeSender.type, senderName, false, errorSummary)
        }
    }
}
