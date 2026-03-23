package io.github.magisk317.relay.domain.pipeline

import android.content.Context
import com.google.gson.Gson
import io.github.magisk317.relay.diagnostics.ForwardFlowLog
import io.github.magisk317.relay.common.utils.XLog
import io.github.magisk317.relay.model.MsgInfo
import io.github.magisk317.relay.model.Sender
import io.github.magisk317.relay.platform.sender.config.BarkSetting
import io.github.magisk317.relay.platform.sender.config.DingtalkGroupRobotSetting
import io.github.magisk317.relay.platform.sender.config.DingtalkInnerRobotSetting
import io.github.magisk317.relay.platform.sender.config.EmailSetting
import io.github.magisk317.relay.platform.sender.config.FeishuAppSetting
import io.github.magisk317.relay.platform.sender.config.FeishuSetting
import io.github.magisk317.relay.platform.sender.config.GotifySetting
import io.github.magisk317.relay.platform.sender.config.NtfySetting
import io.github.magisk317.relay.platform.sender.config.PushplusSetting
import io.github.magisk317.relay.platform.sender.config.ServerchanSetting
import io.github.magisk317.relay.platform.sender.config.SmsSetting
import io.github.magisk317.relay.platform.sender.config.SocketSetting
import io.github.magisk317.relay.platform.sender.config.TelegramSetting
import io.github.magisk317.relay.platform.sender.config.UrlSchemeSetting
import io.github.magisk317.relay.platform.sender.config.WebhookSetting
import io.github.magisk317.relay.platform.sender.config.WeworkAgentSetting
import io.github.magisk317.relay.platform.sender.config.WeworkRobotSetting
import io.github.magisk317.relay.domain.sender.SenderType
import io.github.magisk317.relay.platform.sender.BarkUtils
import io.github.magisk317.relay.platform.sender.DingtalkGroupRobotUtils
import io.github.magisk317.relay.platform.sender.DingtalkInnerRobotUtils
import io.github.magisk317.relay.platform.sender.EmailUtils
import io.github.magisk317.relay.platform.sender.FeishuAppUtils
import io.github.magisk317.relay.platform.sender.FeishuUtils
import io.github.magisk317.relay.platform.sender.GotifyUtils
import io.github.magisk317.relay.platform.sender.NtfyUtils
import io.github.magisk317.relay.platform.sender.PushplusUtils
import io.github.magisk317.relay.platform.sender.ServerchanUtils
import io.github.magisk317.relay.platform.sender.SocketUtils
import io.github.magisk317.relay.platform.sender.SmsUtils
import io.github.magisk317.relay.platform.sender.TelegramUtils
import io.github.magisk317.relay.platform.sender.UrlSchemeUtils
import io.github.magisk317.relay.platform.sender.WebhookUtils
import io.github.magisk317.relay.platform.sender.WeworkAgentUtils
import io.github.magisk317.relay.platform.sender.WeworkRobotUtils
import io.github.magisk317.relay.runtime.BuildConfig

data class SenderDispatchResult(
    val senderId: Long,
    val senderType: Int,
    val senderName: String,
    val success: Boolean,
    val message: String,
)

class DispatchExecutor(private val context: Context) {
    private val gson = Gson()

    suspend fun dispatchToSenders(
        senders: List<Sender>,
        msgInfo: MsgInfo,
        traceId: String? = null,
    ): List<SenderDispatchResult> {
        return senders.map { sender ->
            dispatchToSender(sender, msgInfo, traceId)
        }
    }

    suspend fun dispatchToSender(
        sender: Sender,
        msgInfo: MsgInfo,
        traceId: String? = null,
    ): SenderDispatchResult {
        val senderName = sender.name.ifBlank { "通道${sender.type}" }
        XLog.d("Dispatching to sender: id=%d, type=%d, name=%s", sender.id, sender.type, sender.name)
            ForwardFlowLog.d(traceId, "Dispatch sender start name=$senderName type=${sender.type}")
            try {
                when (sender.type) {
                SenderType.DINGTALK_GROUP_ROBOT -> DingtalkGroupRobotUtils.sendMsg(
                    gson.fromJson(sender.jsonSetting, DingtalkGroupRobotSetting::class.java),
                    msgInfo,
                )
                SenderType.EMAIL -> EmailUtils.sendMsg(
                    gson.fromJson(sender.jsonSetting, EmailSetting::class.java),
                    msgInfo,
                    traceId,
                )
                SenderType.BARK -> BarkUtils.sendMsg(gson.fromJson(sender.jsonSetting, BarkSetting::class.java), msgInfo)
                SenderType.WEBHOOK -> WebhookUtils.sendMsg(gson.fromJson(sender.jsonSetting, WebhookSetting::class.java), msgInfo, traceId)
                SenderType.WEWORK_ROBOT -> WeworkRobotUtils.sendMsg(gson.fromJson(sender.jsonSetting, WeworkRobotSetting::class.java), msgInfo)
                SenderType.WEWORK_AGENT -> WeworkAgentUtils.sendMsg(gson.fromJson(sender.jsonSetting, WeworkAgentSetting::class.java), msgInfo)
                SenderType.SERVERCHAN -> ServerchanUtils.sendMsg(gson.fromJson(sender.jsonSetting, ServerchanSetting::class.java), msgInfo)
                SenderType.PUSHPLUS -> PushplusUtils.sendMsg(gson.fromJson(sender.jsonSetting, PushplusSetting::class.java), msgInfo)
                SenderType.TELEGRAM -> TelegramUtils.sendMsg(gson.fromJson(sender.jsonSetting, TelegramSetting::class.java), msgInfo)
                SenderType.SMS -> {
                    if (!BuildConfig.ENABLE_SMS_CHANNEL) {
                        XLog.w("SMS sender disabled in current distribution, skipping sender [%s]", sender.name)
                    } else {
                        SmsUtils.sendMsg(context, gson.fromJson(sender.jsonSetting, SmsSetting::class.java), msgInfo)
                    }
                }
                SenderType.FEISHU -> FeishuUtils.sendMsg(gson.fromJson(sender.jsonSetting, FeishuSetting::class.java), msgInfo)
                SenderType.GOTIFY -> GotifyUtils.sendMsg(gson.fromJson(sender.jsonSetting, GotifySetting::class.java), msgInfo)
                SenderType.NTFY -> NtfyUtils.sendMsg(gson.fromJson(sender.jsonSetting, NtfySetting::class.java), msgInfo)
                SenderType.DINGTALK_INNER_ROBOT -> DingtalkInnerRobotUtils.sendMsg(
                    gson.fromJson(sender.jsonSetting, DingtalkInnerRobotSetting::class.java),
                    msgInfo,
                )
                SenderType.FEISHU_APP -> FeishuAppUtils.sendMsg(gson.fromJson(sender.jsonSetting, FeishuAppSetting::class.java), msgInfo)
                SenderType.URL_SCHEME -> UrlSchemeUtils.sendMsg(context, gson.fromJson(sender.jsonSetting, UrlSchemeSetting::class.java), msgInfo)
                SenderType.SOCKET -> SocketUtils.sendMsg(gson.fromJson(sender.jsonSetting, SocketSetting::class.java), msgInfo)
                else -> {
                    val message = "Unsupported sender type: ${sender.type}"
                    return SenderDispatchResult(sender.id, sender.type, senderName, false, message)
                }
            }
            ForwardFlowLog.i(traceId, "Dispatch sender success name=$senderName")
            return SenderDispatchResult(sender.id, sender.type, senderName, true, "OK")
        } catch (e: com.google.gson.JsonSyntaxException) {
            ForwardFlowLog.e(traceId, "Dispatch sender json parse failed name=$senderName", e)
            return SenderDispatchResult(sender.id, sender.type, senderName, false, "配置解析失败: ${e.message ?: "JsonSyntaxException"}")
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            ForwardFlowLog.e(traceId, "Dispatch sender failed name=$senderName", e)
            return SenderDispatchResult(sender.id, sender.type, senderName, false, e.message ?: e.javaClass.simpleName)
        }
    }
}
