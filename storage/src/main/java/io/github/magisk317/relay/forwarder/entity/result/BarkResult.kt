package io.github.magisk317.relay.forwarder.entity.result

import androidx.annotation.Keep

@Keep
data class BarkResult(
    var code: Long,
    var message: String,
    var timestamp: Long?,
)