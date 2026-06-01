package io.github.magisk317.relay.sender.result

import androidx.annotation.Keep
import kotlinx.serialization.Serializable as KotlinSerializable

@Keep
@KotlinSerializable
@Suppress("PropertyName")
data class FeishuBotTokenResult(
    var code: Long = -1L,
    var msg: String = "",
    var content: String? = null,
)
