package io.github.magisk317.relay.forwarder.entity.result

import androidx.annotation.Keep

@Keep
data class ServerchanResult(
    var code: Long,
    var message: String,
    var data: Any?,
)