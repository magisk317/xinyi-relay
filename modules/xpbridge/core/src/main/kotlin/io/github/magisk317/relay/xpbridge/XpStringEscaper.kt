package io.github.magisk317.relay.xpbridge

import io.github.magisk317.smscode.runtime.common.utils.StringUtils

object XpStringEscaper {
    fun escape(value: String?): String? = StringUtils.escape(value)
    fun summarizeSender(value: String?): String = StringUtils.summarizeSender(value)
}
