package com.github.magisk317.smscode.forwarder.utils.sender

import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.entity.setting.EmailSetting
import com.github.magisk317.smscode.forwarder.utils.SenderSettingSanitizer
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

object EmailUtils {
    private const val TAG = "EmailUtils"

    suspend fun sendMsg(setting: EmailSetting, msgInfo: MsgInfo, traceId: String? = null) = withContext(Dispatchers.IO) {
        fun t(message: String): String = if (traceId.isNullOrBlank()) message else "[trace=$traceId] $message"
        runCatching {
            val safeSetting = SenderSettingSanitizer.sanitizeEmailSetting(setting)
            normalizeMailType(safeSetting)

            val fromEmail = safeSetting.fromEmail
            val password = safeSetting.pwd
            val host = safeSetting.host
            val port = safeSetting.port.ifBlank { "465" }
            val portInt = port.toIntOrNull() ?: 465
            val recipients = buildRecipients(safeSetting)

            if (fromEmail.isBlank() || password.isBlank() || host.isBlank() || recipients.isEmpty()) {
                SLog.e(TAG, t("Email config invalid"))
                throw IllegalArgumentException("邮箱配置不完整")
            }

            val props = Properties().apply {
                put("mail.smtp.host", host)
                put("mail.smtp.port", port)
                put("mail.smtp.auth", "true")
                put("mail.smtp.ssl.enable", safeSetting.ssl.toString())
                put("mail.smtp.starttls.enable", safeSetting.startTls.toString())
            }

            val session = Session.getInstance(props, object : jakarta.mail.Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(fromEmail, password)
                }
            })

            val message = MimeMessage(session)
            message.setFrom(InternetAddress(fromEmail, safeSetting.fromEmailAlias.ifBlank { fromEmail }))
            message.setRecipients(Message.RecipientType.TO, recipients.map { InternetAddress(it) }.toTypedArray())
            message.subject = if (safeSetting.title.isBlank()) "SmsCode: ${msgInfo.from}" else safeSetting.title
            message.setText(msgInfo.content)

            sendByTransport(session, message, host, portInt, fromEmail, password)
            SLog.i(TAG, t("Email send success"))
        }.onFailure {
            SLog.e(TAG, t("Email send failed"), it)
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
                setting.fromEmail = appendDomain(setting.fromEmail, setting.mailType)
            }
            "@gmail.com" -> {
                setting.host = "smtp.gmail.com"
                setting.port = "465"
                setting.ssl = true
                setting.fromEmail = appendDomain(setting.fromEmail, setting.mailType)
            }
            "@163.com" -> {
                setting.host = "smtp.163.com"
                setting.port = "465"
                setting.ssl = true
                setting.fromEmail = appendDomain(setting.fromEmail, setting.mailType)
            }
            "@126.com" -> {
                setting.host = "smtp.126.com"
                setting.port = "465"
                setting.ssl = true
                setting.fromEmail = appendDomain(setting.fromEmail, setting.mailType)
            }
            "@outlook.com" -> {
                setting.host = "smtp.office365.com"
                setting.port = "587"
                setting.ssl = false
                setting.startTls = true
                setting.fromEmail = appendDomain(setting.fromEmail, setting.mailType)
            }
            "@icloud.com" -> {
                setting.host = "smtp.mail.me.com"
                setting.port = "587"
                setting.ssl = false
                setting.startTls = true
                setting.fromEmail = appendDomain(setting.fromEmail, setting.mailType)
            }
        }
    }

    private fun appendDomain(name: String, domain: String): String {
        if (name.contains("@")) return name
        return "$name$domain"
    }
}
