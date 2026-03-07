package com.github.magisk317.smscode.forwarder.utils

import com.github.magisk317.smscode.forwarder.entity.Sender
import com.github.magisk317.smscode.forwarder.entity.setting.BarkSetting
import com.github.magisk317.smscode.forwarder.entity.setting.DingtalkGroupRobotSetting
import com.github.magisk317.smscode.forwarder.entity.setting.DingtalkInnerRobotSetting
import com.github.magisk317.smscode.forwarder.entity.setting.EmailSetting
import com.github.magisk317.smscode.forwarder.entity.setting.FeishuAppSetting
import com.github.magisk317.smscode.forwarder.entity.setting.FeishuSetting
import com.github.magisk317.smscode.forwarder.entity.setting.GotifySetting
import com.github.magisk317.smscode.forwarder.entity.setting.PushplusSetting
import com.github.magisk317.smscode.forwarder.entity.setting.ServerchanSetting
import com.github.magisk317.smscode.forwarder.entity.setting.SmsSetting
import com.github.magisk317.smscode.forwarder.entity.setting.SocketSetting
import com.github.magisk317.smscode.forwarder.entity.setting.TelegramSetting
import com.github.magisk317.smscode.forwarder.entity.setting.UrlSchemeSetting
import com.github.magisk317.smscode.forwarder.entity.setting.WebhookSetting
import com.github.magisk317.smscode.forwarder.entity.setting.WeworkAgentSetting
import com.github.magisk317.smscode.forwarder.entity.setting.WeworkRobotSetting
import com.google.gson.Gson
import io.github.magisk317.xinyi.relay.storage.BuildConfig

data class SenderValidationResult(
    val valid: Boolean,
    val message: String = "",
)

object SenderValidator {
    private val gson = Gson()

    @Suppress("CyclomaticComplexMethod")
    fun validateForEnable(sender: Sender): SenderValidationResult {
        val safeSender = SenderSettingSanitizer.sanitizeSenderLenient(sender)
        return try {
            when (safeSender.type) {
                SenderType.DINGTALK_GROUP_ROBOT -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, DingtalkGroupRobotSetting::class.java)
                    if (setting.token.isBlank()) invalid("钉钉群机器人 Token 不能为空") else ok()
                }

                SenderType.EMAIL -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, EmailSetting::class.java)
                    val hasRecipient = setting.toEmail.isNotBlank() || setting.recipients.isNotEmpty()
                    if (setting.fromEmail.isBlank() || setting.pwd.isBlank() || !hasRecipient) {
                        invalid("邮件通道信息不完整（发件人/密码/收件人）")
                    } else ok()
                }

                SenderType.BARK -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, BarkSetting::class.java)
                    if (setting.server.isBlank()) invalid("Bark 地址不能为空") else ok()
                }

                SenderType.WEBHOOK -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, WebhookSetting::class.java)
                    when {
                        setting.webServer.isBlank() -> invalid("Webhook 地址不能为空")
                        !BuildConfig.ALLOW_HTTP_WEBHOOK && isHttpWebhookUrl(setting.webServer) ->
                            invalid("当前构建版本仅支持 HTTPS Webhook 地址")
                        else -> ok()
                    }
                }

                SenderType.WEWORK_ROBOT -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, WeworkRobotSetting::class.java)
                    if (setting.webHook.isBlank()) invalid("企业微信群机器人 Webhook 不能为空") else ok()
                }

                SenderType.WEWORK_AGENT -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, WeworkAgentSetting::class.java)
                    if (setting.corpID.isBlank() || setting.agentID.isBlank() || setting.secret.isBlank()) {
                        invalid("企业微信应用 corpID/agentID/secret 不能为空")
                    } else ok()
                }

                SenderType.SERVERCHAN -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, ServerchanSetting::class.java)
                    if (setting.sendKey.isBlank()) invalid("Server酱 SendKey 不能为空") else ok()
                }

                SenderType.TELEGRAM -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, TelegramSetting::class.java)
                    if (setting.apiToken.isBlank() || setting.chatId.isBlank()) {
                        invalid("Telegram API Token 和 Chat ID 不能为空")
                    } else ok()
                }

                SenderType.SMS -> {
                    if (!BuildConfig.ENABLE_SMS_CHANNEL) {
                        invalid("当前构建版本不支持短信通道")
                    } else {
                        val setting = gson.fromJson(safeSender.jsonSetting, SmsSetting::class.java)
                        if (setting.mobiles.isBlank()) invalid("短信通道目标号码不能为空") else ok()
                    }
                }

                SenderType.FEISHU -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, FeishuSetting::class.java)
                    if (setting.webhook.isBlank()) invalid("飞书机器人 Webhook 不能为空") else ok()
                }

                SenderType.PUSHPLUS -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, PushplusSetting::class.java)
                    if (setting.token.isBlank()) invalid("PushPlus Token 不能为空") else ok()
                }

                SenderType.GOTIFY -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, GotifySetting::class.java)
                    if (setting.webServer.isBlank()) invalid("Gotify 地址不能为空") else ok()
                }

                SenderType.DINGTALK_INNER_ROBOT -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, DingtalkInnerRobotSetting::class.java)
                    if (setting.agentID.isBlank() || setting.appKey.isBlank() || setting.appSecret.isBlank() || setting.userIds.isBlank()) {
                        invalid("钉钉内部机器人参数不完整")
                    } else ok()
                }

                SenderType.FEISHU_APP -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, FeishuAppSetting::class.java)
                    if (setting.appId.isBlank() || setting.appSecret.isBlank() || setting.receiveId.isBlank()) {
                        invalid("飞书应用 appId/appSecret/receiveId 不能为空")
                    } else ok()
                }

                SenderType.URL_SCHEME -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, UrlSchemeSetting::class.java)
                    if (setting.urlScheme.isBlank()) invalid("Url Scheme 不能为空") else ok()
                }

                SenderType.SOCKET -> {
                    val setting = gson.fromJson(safeSender.jsonSetting, SocketSetting::class.java)
                    if (setting.address.isBlank() || setting.port <= 0) {
                        invalid("Socket 地址或端口不正确")
                    } else ok()
                }

                else -> invalid("未知通道类型，无法启用")
            }
        } catch (_: Exception) {
            invalid("通道配置格式错误，请检查字段")
        }
    }

    private fun ok(): SenderValidationResult = SenderValidationResult(valid = true)
    private fun invalid(msg: String): SenderValidationResult = SenderValidationResult(valid = false, message = msg)

    private fun isHttpWebhookUrl(url: String): Boolean {
        return url.trim().startsWith(prefix = "http://", ignoreCase = true)
    }
}
