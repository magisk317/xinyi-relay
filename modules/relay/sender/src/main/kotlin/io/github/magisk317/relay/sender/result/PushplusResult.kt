package io.github.magisk317.relay.sender.result

import androidx.annotation.Keep
import kotlinx.serialization.Serializable as KotlinSerializable

@Keep
@KotlinSerializable
data class PushplusResult(
    var code: Long = -1L,
    var msg: String = "",
    var data: String? = null,
    var count: Long? = null,
)
