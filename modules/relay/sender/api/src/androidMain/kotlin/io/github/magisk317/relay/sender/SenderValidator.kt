package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.sender.config.BarkSetting
import io.github.magisk317.relay.sender.config.DingtalkGroupRobotSetting
import io.github.magisk317.relay.sender.config.DingtalkInnerRobotSetting
import io.github.magisk317.relay.sender.config.EmailSetting
import io.github.magisk317.relay.sender.config.FeishuAppSetting
import io.github.magisk317.relay.sender.config.FeishuSetting
import io.github.magisk317.relay.sender.config.GotifySetting
import io.github.magisk317.relay.sender.config.MatrixSetting
import io.github.magisk317.relay.sender.config.NtfySetting
import io.github.magisk317.relay.sender.config.PushdeerSetting
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
import io.github.magisk317.relay.engine.sender.SenderType
import io.github.magisk317.xposed.logging.MagiskOtel

data class SenderValidationResult(
    val valid: Boolean,
    val message: String = "",
)

object SenderValidator {

    @Suppress("CyclomaticComplexMethod")
    fun validateForEnable(
        sender: Sender,
        allowHttpWebhook: Boolean = true,
        enableSmsChannel: Boolean = true,
    ): SenderValidationResult {
        val safeSender = SenderSettingSanitizer.sanitizeSenderLenient(sender)
        val result = try {
            when (safeSender.type) {
                SenderType.DINGTALK_GROUP_ROBOT -> {
                    val setting = SenderSettingJson.decode(DingtalkGroupRobotSetting.serializer(), safeSender.jsonSetting)
                    if (setting.token.isBlank()) invalid("钉钉群机器人 Token 不能为空") else ok()
                }

                SenderType.EMAIL -> {
                    val setting = SenderSettingJson.decode(EmailSetting.serializer(), safeSender.jsonSetting)
                    val hasRecipient = setting.toEmail.isNotBlank() || setting.recipients.isNotEmpty()
                    if (setting.fromEmail.isBlank() || !hasRecipient) {
                        invalid("邮件通道信息不完整（发件人/收件人）")
                    } else if (setting.authMethod == "oauth2") {
                        if (setting.oauth2ClientId.isBlank() || setting.oauth2TenantId.isBlank() ||
                            setting.oauth2CredentialId.isBlank()
                        ) {
                            invalid("OAuth2 凭据不完整（应用ID/租户ID/授权凭证）")
                        } else ok()
                    } else {
                        if (setting.pwd.isBlank()) {
                            invalid("请输入授权码/密码，或切换到 OAuth2 身份验证")
                        } else ok()
                    }
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
                    if (setting.receiveId.isBlank()) {
                        invalid("飞书应用 receiveId 不能为空")
                    } else if (setting.appId.isBlank() || setting.appSecret.isBlank()) {
                        invalid("飞书应用 appId/appSecret 不能为空")
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

                SenderType.PUSHDEER -> {
                    val setting = SenderSettingJson.decode(PushdeerSetting.serializer(), safeSender.jsonSetting)
                    if (setting.pushkey.isBlank()) invalid("PushDeer PushKey 不能为空") else ok()
                }

                SenderType.YUNHU -> {
                    val setting = SenderSettingJson.decode(YunhuSetting.serializer(), safeSender.jsonSetting)
                    when {
                        setting.token.isBlank() -> invalid("云湖机器人 Token 不能为空")
                        setting.recvId.isBlank() -> invalid("云湖接收者 ID 不能为空")
                        else -> ok()
                    }
                }
                SenderType.MATRIX -> {
                    val setting = SenderSettingJson.decode(MatrixSetting.serializer(), safeSender.jsonSetting)
                    when {
                        setting.homeserver.isBlank() -> invalid("Matrix Homeserver 不能为空")
                        setting.legacyAccessToken().isBlank() &&
                            (setting.username.isBlank() || setting.password.isBlank()) ->
                            invalid("Matrix 必须提供 Access Token 或账号密码")
                        setting.roomId.isBlank() -> invalid("Matrix Room ID 不能为空")
                        else -> ok()
                    }
                }
                else -> invalid("未知通道类型，无法启用")
            }
        } catch (_: Exception) {
            invalid("通道配置格式错误，请检查字段")
        }
        MagiskOtel.event(
            name = "sms.sender_runtime",
            attributes = mapOf(
                "result" to if (result.valid) "ok" else "error",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "sender_validate",
                "reason" to if (result.valid) "valid" else "invalid",
                "sender_type" to safeSender.type.toString(),
            ),
            statusOk = result.valid,
        )
        return result
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

    @Suppress("DEPRECATION")
    private fun MatrixSetting.legacyAccessToken(): String = accessToken
}
