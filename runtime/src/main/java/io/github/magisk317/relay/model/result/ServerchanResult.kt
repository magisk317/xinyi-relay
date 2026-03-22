package io.github.magisk317.relay.model.result

import androidx.annotation.Keep

@Keep
data class ServerchanResult(
    var code: Long,
    var message: String,
    var data: Any?,
)