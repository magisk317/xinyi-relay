package io.github.magisk317.relay.model.result

import androidx.annotation.Keep

@Keep
data class TelegramResult(
    var ok: Boolean?,
    var message: String,
    var timestamp: Long?,
)