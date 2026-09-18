package io.github.magisk317.relay.sender.config

import kotlinx.serialization.Serializable as KotlinSerializable
import java.io.Serializable


@KotlinSerializable
data class EmailSetting(
    var mailType: String = "",
    var authEmail: String = "",
    var fromEmail: String = "",
    var pwd: String = "",
    var nickname: String = "",
    var host: String = "",
    var port: String = "",
    var ssl: Boolean = false,
    var startTls: Boolean = false,
    var title: String = "",
    var recipients: MutableMap<String, Pair<String, String>> = mutableMapOf(),
    var toEmail: String = "",
    var keystore: String = "",
    var password: String = "",
    var encryptionProtocol: String = "Plain", //加密协议: S/MIME、OpenPGP、Plain（不传证书）
    var fromEmailAlias: String = "", //发件邮箱别名
    var authMethod: String = "password", // 身份验证方式: "password" | "oauth2"
    var oauth2ClientId: String = "", // Azure AD 应用(客户端) ID（仅用于发起授权）
    var oauth2TenantId: String = "", // Azure AD 租户 ID，多租户应用填 "common"
    var oauth2CredentialId: String = "", // 指向加密存储的 OAuth2 凭证（不含密钥）
) : Serializable {

}
