package com.github.magisk317.smscode.forwarder.entity.setting

import java.io.Serializable

data class EmailSetting(
    var mailType: String = "",
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
) : Serializable {

}
