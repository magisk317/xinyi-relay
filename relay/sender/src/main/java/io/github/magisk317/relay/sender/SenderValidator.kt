package io.github.magisk317.relay.sender

import io.github.magisk317.relay.sender.BuildConfig

import io.github.magisk317.relay.engine.model.Sender
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
import io.github.magisk317.relay.engine.sender.SenderType

data class SenderValidationResult(
    val valid: Boolean,
    val message: String = "",
)

object SenderValidator {

    @Suppress("CyclomaticComplexMethod")
    fun validateForEnable(
        sender: Sender,
        allowHttpWebhook: Boolean = BuildConfig.ALLOW_HTTP_WEBHOOK,
        enableSmsChannel: Boolean = BuildConfig.ENABLE_SMS_CHANNEL,
    ): SenderValidationResult {
        val safeSender = SenderSettingSanitizer.sanitizeSenderLenient(sender)
        return try {
            when (safeSender.type) {
                SenderType.DINGTALK_GROUP_ROBOT -> {
                    val setting = SenderSettingJson.decode(DingtalkGroupRobotSetting.serializer(), safeSender.jsonSetting)
                    if (setting.token.isBlank()) invalid("钉钉群机器人 Token 不能为空") else ok()
                }

                SenderType.EMAIL -> {
                    val setting = SenderSettingJson.decode(EmailSetting.serializer(), safeSender.jsonSetting)
                    val hasRecipient = setting.toEmail.isNotBlank() || setting.recipients.isNotEmpty()
                    if (setting.fromEmail.isBlank() || setting.pwd.isBlank() || !hasRecipient) {
                        invalid("邮件通道信息不完整（发件人/密码/收件人）")
                    } else ok()
                }

                SenderType.BARK -> {
                    val setting = SenderSettingJson.decode(BarkSetting.serializer(), safeSender.jsonSetting)
                    if (setting.server.isBlank()) invalid("Bark 地址不能为空") else ok()
                }

                SenderType.WEBHOOK -> {
                    val setting = SenderSettingJson.decode(WebhookSetting.serializer(), safeSender.jsonSetting)
                    when {
                        setting.webServer.isBlank() -> invalid("Webhook 地址不能为空")
                        !allowHttpWebhook && isHttpWebhookUrl(setting.webServer) ->
                            invalid("当前构建版本仅支持 HTTPS Webhook 地址")
                        else -> ok()
                    }
                }

                SenderType.WEWORK_ROBOT -> {
                    val setting = SenderSettingJson.decode(WeworkRobotSetting.serializer(), safeSender.jsonSetting)
                    if (setting.webHook.isBlank()) invalid("企业微信群机器人 Webhook 不能为空") else ok()
                }

                SenderType.WEWORK_AGENT -> {
                    val setting = SenderSettingJson.decode(WeworkAgentSetting.serializer(), safeSender.jsonSetting)
                    if (setting.corpID.isBlank() || setting.agentID.isBlank() || setting.secret.isBlank()) {
                        invalid("企业微信应用 corpID/agentID/secret 不能为空")
                    } else ok()
                }

                SenderType.SERVERCHAN -> {
                    val setting = SenderSettingJson.decode(ServerchanSetting.serializer(), safeSender.jsonSetting)
                    if (setting.sendKey.isBlank()) invalid("Server酱 SendKey 不能为空") else ok()
                }

                SenderType.TELEGRAM -> {
                    val setting = SenderSettingJson.decode(TelegramSetting.serializer(), safeSender.jsonSetting)
                    if (setting.apiToken.isBlank() || setting.chatId.isBlank()) {
                        invalid("Telegram API Token 和 Chat ID 不能为空")
                    } else ok()
                }

                SenderType.SMS -> {
                    if (!enableSmsChannel) {
                        invalid("当前构建版本不支持短信通道")
                    } else {
                        val setting = SenderSettingJson.decode(SmsSetting.serializer(), safeSender.jsonSetting)
                        if (setting.mobiles.isBlank()) invalid("短信通道目标号码不能为空") else ok()
                    }
                }

                SenderType.FEISHU -> {
                    val setting = SenderSettingJson.decode(FeishuSetting.serializer(), safeSender.jsonSetting)
                    if (setting.webhook.isBlank()) invalid("飞书机器人 Webhook 不能为空") else ok()
                }

                SenderType.PUSHPLUS -> {
                    val setting = SenderSettingJson.decode(PushplusSetting.serializer(), safeSender.jsonSetting)
                    if (setting.token.isBlank()) invalid("PushPlus Token 不能为空") else ok()
                }

                SenderType.GOTIFY -> {
                    val setting = SenderSettingJson.decode(GotifySetting.serializer(), safeSender.jsonSetting)
                    if (setting.webServer.isBlank()) invalid("Gotify 地址不能为空") else ok()
                }

                SenderType.NTFY -> {
                    val setting = SenderSettingJson.decode(NtfySetting.serializer(), safeSender.jsonSetting)
                    when {
                        setting.server.isBlank() -> invalid("ntfy Server 不能为空")
                        setting.topic.isBlank() -> invalid("ntfy Topic 不能为空")
                        !isValidNtfyPriority(setting.priority) -> invalid("ntfy 优先级仅支持 1-5")
                        else -> ok()
                    }
                }

                SenderType.DINGTALK_INNER_ROBOT -> {
                    val setting = SenderSettingJson.decode(DingtalkInnerRobotSetting.serializer(), safeSender.jsonSetting)
                    if (setting.agentID.isBlank() || setting.appKey.isBlank() || setting.appSecret.isBlank() || setting.userIds.isBlank()) {
                        invalid("钉钉内部机器人参数不完整")
                    } else ok()
                }

                SenderType.FEISHU_APP -> {
                    val setting = SenderSettingJson.decode(FeishuAppSetting.serializer(), safeSender.jsonSetting)
                    if (setting.appId.isBlank() || setting.appSecret.isBlank() || setting.receiveId.isBlank()) {
                        invalid("飞书应用 appId/appSecret/receiveId 不能为空")
                    } else ok()
                }

                SenderType.URL_SCHEME -> {
                    val setting = SenderSettingJson.decode(UrlSchemeSetting.serializer(), safeSender.jsonSetting)
                    if (setting.urlScheme.isBlank()) invalid("Url Scheme 不能为空") else ok()
                }

                SenderType.SOCKET -> {
                    val setting = SenderSettingJson.decode(SocketSetting.serializer(), safeSender.jsonSetting)
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

    private fun isValidNtfyPriority(priority: String): Boolean {
        val normalized = priority.trim()
        if (normalized.isEmpty()) return true
        return normalized.toIntOrNull() in 1..5
    }
}
