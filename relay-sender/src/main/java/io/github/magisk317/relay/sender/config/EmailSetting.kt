package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class EmailSetting(
    @SerializedName(value = "mailType", alternate = ["o"])
    var mailType: String = "",
    @SerializedName(value = "authEmail", alternate = ["p"])
    var authEmail: String = "",
    @SerializedName(value = "fromEmail", alternate = ["q"])
    var fromEmail: String = "",
    @SerializedName(value = "pwd", alternate = ["r"])
    var pwd: String = "",
    @SerializedName(value = "nickname", alternate = ["s"])
    var nickname: String = "",
    @SerializedName(value = "host", alternate = ["t"])
    var host: String = "",
    @SerializedName(value = "port", alternate = ["u"])
    var port: String = "",
    @SerializedName(value = "ssl", alternate = ["v"])
    var ssl: Boolean = false,
    @SerializedName(value = "startTls", alternate = ["w"])
    var startTls: Boolean = false,
    @SerializedName(value = "title", alternate = ["x"])
    var title: String = "",
    @SerializedName(value = "recipients", alternate = ["y"])
    var recipients: MutableMap<String, Pair<String, String>> = mutableMapOf(),
    @SerializedName(value = "toEmail", alternate = ["z"])
    var toEmail: String = "",
    @SerializedName(value = "keystore", alternate = ["A"])
    var keystore: String = "",
    @SerializedName(value = "password", alternate = ["B"])
    var password: String = "",
    @SerializedName(value = "encryptionProtocol", alternate = ["C"])
    var encryptionProtocol: String = "Plain", //加密协议: S/MIME、OpenPGP、Plain（不传证书）
    @SerializedName(value = "fromEmailAlias", alternate = ["D"])
    var fromEmailAlias: String = "", //发件邮箱别名
) : Serializable {

}
