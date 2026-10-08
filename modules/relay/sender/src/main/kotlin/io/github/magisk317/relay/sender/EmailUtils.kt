package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.service.SenderRuntimeServiceRegistry
import io.github.magisk317.relay.sender.config.EmailSetting
import io.github.magisk317.relay.sender.SenderSettingSanitizer
import com.sun.mail.smtp.SMTPTransport
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.NoSuchProviderException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.URLName
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import io.github.magisk317.xposed.logging.MagiskOtel

object EmailUtils {
    private const val TAG = "EmailUtils"

    class OAuth2Exception(message: String) : RuntimeException(message)

    /** Whether the setting is configured for OAuth2 authentication. */
    private fun isOAuth2Configured(setting: EmailSetting): Boolean =
        setting.authMethod == "oauth2" && setting.oauth2CredentialId.isNotBlank()

    /**
     * Ensure a valid OAuth2 access token is available for the given credential.
     * Refreshes if missing or near expiry. Returns the access token.
     *
     * Token rotation is persisted through [OAuth2Service], so subsequent sends
     * see the updated refresh token.
     */
    private suspend fun ensureValidAccessToken(credentialId: String, nowMs: Long): String {
        val oauth = SenderRuntimeServiceRegistry.installedOrNull()?.emailOAuth() as? OAuth2Service
            ?: throw OAuth2Exception("OAuth2 服务未初始化")
        return oauth.ensureValidAccessToken(credentialId, nowMs)
    }

    private fun emitForward(
        result: String,
        reason: String,
        durationMs: Long,
        statusOk: Boolean = true,
    ) {
        MagiskOtel.event(
            name = "sms.forward",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "email_send",
                "reason" to reason,
                "sender_type" to "email",
            ),
            statusOk = statusOk,
        )
    }

    suspend fun sendMsg(setting: EmailSetting, msgInfo: MsgInfo, traceId: String? = null) = withContext(Dispatchers.IO) {
        fun t(message: String): String = if (traceId.isNullOrBlank()) message else "[trace=$traceId] $message"
        val startedAt = System.nanoTime()
        runCatching {
            val safeSetting = SenderSettingSanitizer.sanitizeEmailSetting(setting)
            normalizeMailType(safeSetting)

            val fromEmail = safeSetting.fromEmail
            val authEmail = safeSetting.authEmail.ifBlank { fromEmail }
            val host = safeSetting.host
            val port = safeSetting.port.ifBlank { "465" }
            val portInt = port.toIntOrNull() ?: 465
            val recipients = buildRecipients(safeSetting)

            val useOAuth2 = isOAuth2Configured(safeSetting)
            val password = safeSetting.pwd

            if (fromEmail.isBlank() || host.isBlank() || recipients.isEmpty()) {
                SLog.e(TAG, t("Email config invalid"))
                throw IllegalArgumentException("邮箱配置不完整")
            }
            if (!useOAuth2 && password.isBlank()) {
                SLog.e(TAG, t("Email config invalid: password required for basic auth"))
                throw IllegalArgumentException("请输入授权码/密码，或切换到 OAuth2 身份验证")
            }

            val props = Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port)
                put("mail.smtp.auth", "true")
                put("mail.smtp.ssl.enable", safeSetting.ssl.toString())
                put("mail.smtp.starttls.enable", safeSetting.startTls.toString())
            }

            // For OAuth2, the access token is obtained via the service and used
            // both for the authenticator and the transport fallback path.
            var oauthAccessToken: String? = null
            val session = if (useOAuth2) {
                val accessToken = ensureValidAccessToken(safeSetting.oauth2CredentialId, System.currentTimeMillis())
                oauthAccessToken = accessToken
                props.put("mail.smtp.auth.mechanisms", "xoauth2")
                SLog.i(TAG, t("Using XOAUTH2 authentication"))
                Session.getInstance(props, object : jakarta.mail.Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(authEmail, accessToken)
                    }
                })
            } else {
                Session.getInstance(props, object : jakarta.mail.Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(authEmail, password)
                    }
                })
            }

            val message = MimeMessage(session)
            message.setFrom(
                InternetAddress(
                    fromEmail,
                    safeSetting.fromEmailAlias.ifBlank {
                        safeSetting.nickname.ifBlank { fromEmail }
                    },
                ),
            )
            message.setRecipients(Message.RecipientType.TO, recipients.map { InternetAddress(it) }.toTypedArray())
            message.subject = SenderTemplateRenderer.renderTitle(safeSetting.title, msgInfo)
            message.setText(msgInfo.content)

            val transportPassword = oauthAccessToken ?: password
            sendByTransport(session, message, host, portInt, authEmail, transportPassword)
            SLog.i(TAG, t("Email send success"))
            emitForward(
                result = "ok",
                reason = "success",
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            )
        }.onFailure {
            SLog.e(TAG, t("Email send failed"), it)
            emitForward(
                result = "error",
                reason = it.javaClass.simpleName,
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                statusOk = false,
            )
        }.getOrElse { throw it }
    }

    private fun sendByTransport(
        session: Session,
        message: MimeMessage,
        host: String,
        port: Int,
        fromEmail: String,
        password: String,
    ) {
        runCatching {
            Transport.send(message)
        }.onFailure { err ->
            if (!err.isSmtpProviderMissing()) {
                throw err
            }
            // Fallback path for builds where smtp provider metadata is stripped.
            val transport = SMTPTransport(session, URLName("smtp", host, port, null, fromEmail, password))
            try {
                transport.connect(host, port, fromEmail, password)
                transport.sendMessage(message, message.allRecipients)
            } finally {
                runCatching { transport.close() }
            }
        }.getOrThrow()
    }

    private fun Throwable.isSmtpProviderMissing(): Boolean {
        if (this is NoSuchProviderException) return true
        if (this is MessagingException && message?.contains("smtp", ignoreCase = true) == true) return true
        return cause?.isSmtpProviderMissing() == true
    }

    private fun buildRecipients(setting: EmailSetting): List<String> {
        val fromMap = setting.recipients.keys.toList().filter { it.isNotBlank() }
        if (fromMap.isNotEmpty()) return fromMap
        return setting.toEmail
            .replace("[,，;；]".toRegex(), ",")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun normalizeMailType(setting: EmailSetting) {
        when (setting.mailType) {
            "@qq.com", "@foxmail.com" -> {
                setting.host = "smtp.qq.com"
                setting.port = "465"
                setting.ssl = true
                setting.authEmail = appendDomainIfNeeded(setting.authEmail, setting.mailType)
                setting.fromEmail = appendDomainIfNeeded(setting.fromEmail, setting.mailType)
            }
            "@gmail.com" -> {
                setting.host = "smtp.gmail.com"
                setting.port = "465"
                setting.ssl = true
                setting.authEmail = appendDomainIfNeeded(setting.authEmail, setting.mailType)
                setting.fromEmail = appendDomainIfNeeded(setting.fromEmail, setting.mailType)
            }
            "@163.com" -> {
                setting.host = "smtp.163.com"
                setting.port = "465"
                setting.ssl = true
                setting.authEmail = appendDomainIfNeeded(setting.authEmail, setting.mailType)
                setting.fromEmail = appendDomainIfNeeded(setting.fromEmail, setting.mailType)
            }
            "@126.com" -> {
                setting.host = "smtp.126.com"
                setting.port = "465"
                setting.ssl = true
                setting.authEmail = appendDomainIfNeeded(setting.authEmail, setting.mailType)
                setting.fromEmail = appendDomainIfNeeded(setting.fromEmail, setting.mailType)
            }
            "@outlook.com" -> {
                setting.host = "smtp.office365.com"
                setting.port = "587"
                setting.ssl = false
                setting.startTls = true
                setting.authEmail = appendDomainIfNeeded(setting.authEmail, setting.mailType)
                setting.fromEmail = appendDomainIfNeeded(setting.fromEmail, setting.mailType)
            }
            "@icloud.com" -> {
                setting.host = "smtp.mail.me.com"
                setting.port = "587"
                setting.ssl = false
                setting.startTls = true
                setting.authEmail = appendDomainIfNeeded(setting.authEmail, setting.mailType)
                setting.fromEmail = appendDomainIfNeeded(setting.fromEmail, setting.mailType)
            }
        }
    }

    private fun appendDomainIfNeeded(name: String, domain: String): String {
        if (name.isBlank()) return name
        return appendDomain(name, domain)
    }

    private fun appendDomain(name: String, domain: String): String {
        if (name.contains("@")) return name
        return "$name$domain"
    }
}
