package io.github.magisk317.relay.sender.result

import androidx.annotation.Keep
import kotlinx.serialization.Serializable as KotlinSerializable
import kotlinx.serialization.json.JsonElement

@Keep
@KotlinSerializable
data class ServerchanResult(
    var code: Long = -1L,
    var message: String = "",
    var data: JsonElement? = null,
)
