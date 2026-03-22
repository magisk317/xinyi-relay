package io.github.magisk317.relay.model.result

import androidx.annotation.Keep

@Keep
data class BarkResult(
    var code: Long,
    var message: String,
    var timestamp: Long?,
)