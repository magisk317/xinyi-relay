package io.github.magisk317.relay.sender.result

import androidx.annotation.Keep
import kotlinx.serialization.Serializable as KotlinSerializable

@Keep
@KotlinSerializable
@Suppress("PropertyName")
data class WeworkAgentResult(
    var errcode: Long = -1L,
    var errmsg: String = "",
    //获取access_token返回
    var access_token: String? = null,
    var expires_in: Long? = null,
    //发送接口返回
    var msgid: String? = null,
)
