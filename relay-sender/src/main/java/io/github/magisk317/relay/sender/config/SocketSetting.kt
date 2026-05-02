package io.github.magisk317.relay.sender.config

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class SocketSetting(
    @SerializedName(value = "method", alternate = ["o"])
    val method: String = "MQTT",
    @SerializedName(value = "address", alternate = ["p"])
    var address: String = "", //IP地址
    @SerializedName(value = "port", alternate = ["q"])
    val port: Int = 0, //端口号
    @SerializedName(value = "msgTemplate", alternate = ["r"])
    val msgTemplate: String = "", //消息模板
    @SerializedName(value = "secret", alternate = ["s"])
    val secret: String = "", //签名密钥
    @SerializedName(value = "response", alternate = ["t"])
    val response: String = "", //成功应答关键字
    @SerializedName(value = "username", alternate = ["u"])
    val username: String = "", //用户名
    @SerializedName(value = "password", alternate = ["v"])
    val password: String = "", //密码
    @SerializedName(value = "inCharset", alternate = ["w"])
    val inCharset: String = "", //输入编码
    @SerializedName(value = "outCharset", alternate = ["x"])
    val outCharset: String = "", //输出编码
    @SerializedName(value = "inMessageTopic", alternate = ["y"])
    val inMessageTopic: String = "", //Mqtt专属，输入信息响应主题，即接收对应主题的消息
    @SerializedName(value = "outMessageTopic", alternate = ["z"])
    val outMessageTopic: String = "", //Mqtt专属，输出信息响应主题，即发送对应主题的消息
    @SerializedName(value = "uriType", alternate = ["A"])
    val uriType: String = "tcp", //Mqtt专属，通信方式 默认为tcp
    @SerializedName(value = "path", alternate = ["B"])
    val path: String = "", //Mqtt专属，通信路径，用于在使用ws进行通信时设置uri，最后的访问结果为"${uriType}://${address}:${port}${path}"
    @SerializedName(value = "clientId", alternate = ["C"])
    val clientId: String = "", //Mqtt专属，客户端ID，如果为空则为随机值
    @SerializedName(value = "qos", alternate = ["D"])
    val qos: Int = 0, //Mqtt专属，QoS服务质量
    @SerializedName(value = "retained", alternate = ["E"])
    val retained: Boolean = false, //Mqtt专属，是否保留消息（Retained Message）
) : Serializable {

}
