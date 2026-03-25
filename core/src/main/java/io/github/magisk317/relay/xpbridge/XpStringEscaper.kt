package io.github.magisk317.relay.xpbridge

import io.github.magisk317.relay.common.utils.StringUtils

object XpStringEscaper {
    fun escape(value: String?): String? = StringUtils.escape(value)
    fun summarizeSender(value: String?): String = StringUtils.summarizeSender(value)
    fun summarizeBody(value: String?): String = StringUtils.summarizeBody(value)
    fun summarizeCode(value: String?): String = StringUtils.summarizeCode(value)
}
