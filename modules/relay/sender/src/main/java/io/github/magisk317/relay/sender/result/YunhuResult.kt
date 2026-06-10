package io.github.magisk317.relay.sender.result

import androidx.annotation.Keep
import kotlinx.serialization.Serializable as KotlinSerializable

@Keep
@KotlinSerializable
data class YunhuResult(
    var code: Long = -1L,
    var msg: String = "",
)
