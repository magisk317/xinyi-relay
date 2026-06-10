package io.github.magisk317.relay.sender.result

import androidx.annotation.Keep
import kotlinx.serialization.Serializable as KotlinSerializable

@Keep
@KotlinSerializable
data class DingtalkResult(
    var errcode: Long = -1L,
    var errmsg: String = "",
)
