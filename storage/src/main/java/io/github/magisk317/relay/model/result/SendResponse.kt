package io.github.magisk317.relay.model.result

import androidx.annotation.Keep

@Keep
data class SendResponse(
    var logId: Long,
    var status: Int = 0,
    var response: String = "",
)