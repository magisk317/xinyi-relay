package io.github.magisk317.relay.xpbridge

import io.github.magisk317.relay.common.utils.StringUtils

object XpStringEscaper {
    fun escape(value: String?): String? = StringUtils.escape(value)
}
