package io.github.magisk317.relay.sender.result

import androidx.annotation.Keep
import kotlinx.serialization.Serializable as KotlinSerializable

@Keep
@KotlinSerializable
@Suppress("PropertyName")
data class FeishuAppResult(
    var code: Long = -1L,
    var msg: String = "",
    //获取access_token返回
    var tenant_access_token: String? = null,
    var expire: Long? = null,
    //发送接口返回
    var content: String? = null,
)
