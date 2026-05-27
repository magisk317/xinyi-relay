package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.sender.SenderType
import kotlinx.serialization.Serializable

@Serializable
enum class SenderSettingFieldType {
    TEXT,
    SECRET,
    BOOLEAN,
    INTEGER,
    STRING_MAP,
    EMAIL_RECIPIENTS,
    PROXY_TYPE,
}

@Serializable
data class SenderSettingFieldOption(
    val value: String,
)

@Serializable
data class SenderSettingFieldMetadata(
    val name: String,
    val type: SenderSettingFieldType = SenderSettingFieldType.TEXT,
    val aliases: List<String> = emptyList(),
    val requiredForEnable: Boolean = false,
    val defaultValue: String? = null,
    val options: List<SenderSettingFieldOption> = emptyList(),
)

@Serializable
data class SenderSettingSchema(
    val senderType: Int,
    val fields: List<SenderSettingFieldMetadata>,
)

object SenderSettingSchemas {
    val all: List<SenderSettingSchema> = listOf(
        schema(
            SenderType.DINGTALK_GROUP_ROBOT,
            field("token", SenderSettingFieldType.SECRET, requiredForEnable = true, aliases = arrayOf("o")),
            field("secret", SenderSettingFieldType.SECRET, "p"),
            field("atAll", SenderSettingFieldType.BOOLEAN, "q", defaultValue = "false"),
            field("atMobiles", aliases = arrayOf("r")),
            field("atDingtalkIds", aliases = arrayOf("s")),
            field("msgtype", aliases = arrayOf("t"), defaultValue = "text", options = arrayOf("text", "markdown")),
            field("titleTemplate", aliases = arrayOf("u")),
        ),
        schema(
            SenderType.EMAIL,
            field("mailType", aliases = arrayOf("o")),
            field("authEmail", aliases = arrayOf("p")),
            field("fromEmail", requiredForEnable = true, aliases = arrayOf("q")),
            field("pwd", SenderSettingFieldType.SECRET, requiredForEnable = true, aliases = arrayOf("r")),
            field("nickname", aliases = arrayOf("s")),
            field("host", aliases = arrayOf("t")),
            field("port", aliases = arrayOf("u"), defaultValue = "465"),
            field("ssl", SenderSettingFieldType.BOOLEAN, "v", defaultValue = "true"),
            field("startTls", SenderSettingFieldType.BOOLEAN, "w"),
            field("title", aliases = arrayOf("x")),
            field("recipients", SenderSettingFieldType.EMAIL_RECIPIENTS, requiredForEnable = true, aliases = arrayOf("y")),
            field("toEmail", requiredForEnable = true, aliases = arrayOf("z")),
            field("keystore", aliases = arrayOf("A")),
            field("password", SenderSettingFieldType.SECRET, "B"),
            field("encryptionProtocol", aliases = arrayOf("C")),
            field("fromEmailAlias", aliases = arrayOf("D")),
        ),
        schema(
            SenderType.BARK,
            field("server", requiredForEnable = true, aliases = arrayOf("o")),
            field("group", aliases = arrayOf("p")),
            field("icon", aliases = arrayOf("q")),
            field("sound", aliases = arrayOf("r")),
            field("badge", aliases = arrayOf("s")),
            field("url", aliases = arrayOf("t")),
            field("level", aliases = arrayOf("u")),
            field("title", aliases = arrayOf("v")),
            field(
                "transformation",
                aliases = arrayOf("w"),
                defaultValue = "none",
                options = arrayOf("none", "AES/GCM/NoPadding", "AES/CBC/PKCS5Padding"),
            ),
            field("key", SenderSettingFieldType.SECRET, "x"),
            field("iv", SenderSettingFieldType.SECRET, "y"),
            field("call", aliases = arrayOf("z")),
            field("autoCopy", aliases = arrayOf("A")),
        ),
        schema(
            SenderType.WEBHOOK,
            field("method", aliases = arrayOf("o"), defaultValue = "POST", options = arrayOf("GET", "POST")),
            field("webServer", requiredForEnable = true, aliases = arrayOf("p")),
            field("secret", SenderSettingFieldType.SECRET, "q"),
            field("response", aliases = arrayOf("r")),
            field("webParams", aliases = arrayOf("s")),
            field("headers", SenderSettingFieldType.STRING_MAP, "t"),
            field("proxyType", SenderSettingFieldType.PROXY_TYPE, "u", defaultValue = "DIRECT", options = arrayOf("DIRECT", "HTTP", "SOCKS")),
            field("proxyHost", aliases = arrayOf("v")),
            field("proxyPort", aliases = arrayOf("w")),
            field("proxyAuthenticator", SenderSettingFieldType.BOOLEAN, "x"),
            field("proxyUsername", aliases = arrayOf("y")),
            field("proxyPassword", SenderSettingFieldType.SECRET, "z"),
        ),
        schema(
            SenderType.WEWORK_ROBOT,
            field("webHook", requiredForEnable = true, aliases = arrayOf("o")),
            field("msgType", aliases = arrayOf("p"), defaultValue = "text", options = arrayOf("text", "markdown")),
            field("atAll", SenderSettingFieldType.BOOLEAN, "q", defaultValue = "false"),
            field("atUserIds", aliases = arrayOf("r")),
            field("atMobiles", aliases = arrayOf("s")),
        ),
        schema(
            SenderType.WEWORK_AGENT,
            field("corpID", requiredForEnable = true, aliases = arrayOf("o")),
            field("agentID", requiredForEnable = true, aliases = arrayOf("p")),
            field("secret", SenderSettingFieldType.SECRET, requiredForEnable = true, aliases = arrayOf("q")),
            field("atAll", SenderSettingFieldType.BOOLEAN, "r", defaultValue = "false"),
            field("toUser", aliases = arrayOf("s"), defaultValue = "@all"),
            field("toParty", aliases = arrayOf("t")),
            field("toTag", aliases = arrayOf("u")),
            field("proxyType", SenderSettingFieldType.PROXY_TYPE, "v", defaultValue = "DIRECT", options = arrayOf("DIRECT", "HTTP", "SOCKS")),
            field("proxyHost", aliases = arrayOf("w")),
            field("proxyPort", aliases = arrayOf("x")),
            field("proxyAuthenticator", SenderSettingFieldType.BOOLEAN, "y"),
            field("proxyUsername", aliases = arrayOf("z")),
            field("proxyPassword", SenderSettingFieldType.SECRET, "A"),
            field("customizeAPI", aliases = arrayOf("B"), defaultValue = "https://qyapi.weixin.qq.com"),
        ),
        schema(
            SenderType.SERVERCHAN,
            field("sendKey", SenderSettingFieldType.SECRET, requiredForEnable = true, aliases = arrayOf("o")),
            field("channel", aliases = arrayOf("p")),
            field("openid", aliases = arrayOf("q")),
            field("titleTemplate", aliases = arrayOf("r")),
        ),
        schema(
            SenderType.TELEGRAM,
            field("method", aliases = arrayOf("o"), defaultValue = "POST", options = arrayOf("GET", "POST")),
            field("apiBase", aliases = arrayOf("z"), defaultValue = "https://api.telegram.org"),
            field("apiToken", SenderSettingFieldType.SECRET, requiredForEnable = true, aliases = arrayOf("p")),
            field("chatId", requiredForEnable = true, aliases = arrayOf("q")),
            field("messageThreadId", aliases = arrayOf("topicId", "topic_id", "message_thread_id", "r")),
            field("proxyType", SenderSettingFieldType.PROXY_TYPE, "s", defaultValue = "DIRECT", options = arrayOf("DIRECT", "HTTP", "SOCKS")),
            field("proxyHost", aliases = arrayOf("t")),
            field("proxyPort", aliases = arrayOf("u")),
            field("proxyAuthenticator", SenderSettingFieldType.BOOLEAN, "v"),
            field("proxyUsername", aliases = arrayOf("w")),
            field("proxyPassword", SenderSettingFieldType.SECRET, "x"),
            field("parseMode", aliases = arrayOf("y"), defaultValue = "HTML", options = arrayOf("HTML", "MarkdownV2")),
        ),
        schema(
            SenderType.SMS,
            field("simSlot", SenderSettingFieldType.INTEGER, "o", defaultValue = "0"),
            field("mobiles", requiredForEnable = true, aliases = arrayOf("p")),
            field("onlyNoNetwork", SenderSettingFieldType.BOOLEAN, "q", defaultValue = "false"),
        ),
        schema(
            SenderType.FEISHU,
            field("webhook", requiredForEnable = true, aliases = arrayOf("o")),
            field("secret", SenderSettingFieldType.SECRET, "p"),
            field("msgType", aliases = arrayOf("q"), defaultValue = "interactive", options = arrayOf("interactive", "text")),
            field("titleTemplate", aliases = arrayOf("r")),
            field("messageCard", aliases = arrayOf("s")),
        ),
        schema(
            SenderType.PUSHPLUS,
            field("website", aliases = arrayOf("o"), defaultValue = "www.pushplus.plus"),
            field("token", SenderSettingFieldType.SECRET, requiredForEnable = true, aliases = arrayOf("p")),
            field("topic", aliases = arrayOf("q")),
            field("template", aliases = arrayOf("r"), defaultValue = "html"),
            field("channel", aliases = arrayOf("s"), defaultValue = "wechat"),
            field("webhook", aliases = arrayOf("t")),
            field("callbackUrl", aliases = arrayOf("u")),
            field("validTime", aliases = arrayOf("v")),
            field("titleTemplate", aliases = arrayOf("w")),
        ),
        schema(
            SenderType.GOTIFY,
            field("webServer", requiredForEnable = true, aliases = arrayOf("o")),
            field("title", aliases = arrayOf("p")),
            field("priority", aliases = arrayOf("q"), defaultValue = "0"),
        ),
        schema(
            SenderType.DINGTALK_INNER_ROBOT,
            field("agentID", requiredForEnable = true, aliases = arrayOf("o")),
            field("appKey", SenderSettingFieldType.SECRET, requiredForEnable = true, aliases = arrayOf("p")),
            field("appSecret", SenderSettingFieldType.SECRET, requiredForEnable = true, aliases = arrayOf("q")),
            field("userIds", requiredForEnable = true, aliases = arrayOf("r")),
            field("msgKey", aliases = arrayOf("s"), defaultValue = "sampleText", options = arrayOf("sampleText", "sampleMarkdown")),
            field("titleTemplate", aliases = arrayOf("t")),
            field("proxyType", SenderSettingFieldType.PROXY_TYPE, "u", defaultValue = "DIRECT", options = arrayOf("DIRECT", "HTTP", "SOCKS")),
            field("proxyHost", aliases = arrayOf("v")),
            field("proxyPort", aliases = arrayOf("w")),
            field("proxyAuthenticator", SenderSettingFieldType.BOOLEAN, "x"),
            field("proxyUsername", aliases = arrayOf("y")),
            field("proxyPassword", SenderSettingFieldType.SECRET, "z"),
        ),
        schema(
            SenderType.FEISHU_APP,
            field("appId", SenderSettingFieldType.SECRET, requiredForEnable = true, aliases = arrayOf("o")),
            field("appSecret", SenderSettingFieldType.SECRET, requiredForEnable = true, aliases = arrayOf("p")),
            field("receiveId", requiredForEnable = true, aliases = arrayOf("q")),
            field("msgType", aliases = arrayOf("r"), defaultValue = "interactive", options = arrayOf("interactive", "text")),
            field("titleTemplate", aliases = arrayOf("s")),
            field(
                "receiveIdType",
                aliases = arrayOf("t"),
                defaultValue = "user_id",
                options = arrayOf("user_id", "open_id", "union_id", "email", "chat_id"),
            ),
            field("messageCard", aliases = arrayOf("u")),
        ),
        schema(
            SenderType.URL_SCHEME,
            field("urlScheme", requiredForEnable = true, aliases = arrayOf("o")),
        ),
        schema(
            SenderType.SOCKET,
            field("method", aliases = arrayOf("o"), defaultValue = "MQTT", options = arrayOf("TCP", "UDP", "MQTT")),
            field("address", requiredForEnable = true, aliases = arrayOf("p")),
            field("port", SenderSettingFieldType.INTEGER, requiredForEnable = true, aliases = arrayOf("q")),
            field("msgTemplate", aliases = arrayOf("r"), defaultValue = "{\"msg\":\"[msg]\"}"),
            field("secret", SenderSettingFieldType.SECRET, "s"),
            field("response", aliases = arrayOf("t")),
            field("username", aliases = arrayOf("u")),
            field("password", SenderSettingFieldType.SECRET, "v"),
            field("inCharset", aliases = arrayOf("w")),
            field("outCharset", aliases = arrayOf("x")),
            field("inMessageTopic", aliases = arrayOf("y")),
            field("outMessageTopic", aliases = arrayOf("z"), defaultValue = "relay/default"),
            field("uriType", aliases = arrayOf("A")),
            field("path", aliases = arrayOf("B")),
            field("clientId", aliases = arrayOf("C")),
            field("qos", SenderSettingFieldType.INTEGER, "D"),
            field("retained", SenderSettingFieldType.BOOLEAN, "E"),
        ),
        schema(
            SenderType.NTFY,
            field("server", requiredForEnable = true, aliases = arrayOf("o")),
            field("topic", requiredForEnable = true, aliases = arrayOf("p")),
            field("token", SenderSettingFieldType.SECRET, "q"),
            field("title", aliases = arrayOf("r")),
            field("priority", aliases = arrayOf("s"), defaultValue = "3"),
            field("tags", aliases = arrayOf("t")),
        ),
    )

    val byType: Map<Int, SenderSettingSchema> = all.associateBy { it.senderType }

    fun schemaFor(type: Int): SenderSettingSchema? = byType[type]

    fun fieldsFor(type: Int): List<SenderSettingFieldMetadata> = schemaFor(type)?.fields.orEmpty()

    private fun schema(
        senderType: Int,
        vararg fields: SenderSettingFieldMetadata,
    ): SenderSettingSchema = SenderSettingSchema(senderType, fields.toList())

    private fun field(
        name: String,
        type: SenderSettingFieldType = SenderSettingFieldType.TEXT,
        vararg aliases: String,
        requiredForEnable: Boolean = false,
        defaultValue: String? = null,
        options: Array<String> = emptyArray(),
    ): SenderSettingFieldMetadata {
        return SenderSettingFieldMetadata(
            name = name,
            type = type,
            aliases = aliases.toList(),
            requiredForEnable = requiredForEnable,
            defaultValue = defaultValue,
            options = options.map { SenderSettingFieldOption(it) },
        )
    }

    private fun field(
        name: String,
        type: SenderSettingFieldType = SenderSettingFieldType.TEXT,
        requiredForEnable: Boolean = false,
        defaultValue: String? = null,
        options: Array<String> = emptyArray(),
        aliases: Array<String>,
    ): SenderSettingFieldMetadata {
        return SenderSettingFieldMetadata(
            name = name,
            type = type,
            aliases = aliases.toList(),
            requiredForEnable = requiredForEnable,
            defaultValue = defaultValue,
            options = options.map { SenderSettingFieldOption(it) },
        )
    }
}
