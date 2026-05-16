package io.github.magisk317.relay.sender.result

import androidx.annotation.Keep
import kotlinx.serialization.Serializable as KotlinSerializable

@Keep
@KotlinSerializable
data class BarkResult(
    var code: Long = -1L,
    var message: String = "",
    var timestamp: Long? = null,
)
