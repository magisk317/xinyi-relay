package io.github.magisk317.relay.sender.result

import androidx.annotation.Keep
import kotlinx.serialization.Serializable as KotlinSerializable

@Keep
@KotlinSerializable
data class GotifyResult(
    //失败返回
    var errorCode: Long? = null,
    var error: String? = null,
    var errorDescription: String? = null,
    //成功返回
    var id: Long? = null,
    var appid: Long? = null,
    var title: String? = null,
    var message: String? = null,
    var priority: Long? = null,
    var date: String? = null,
)
