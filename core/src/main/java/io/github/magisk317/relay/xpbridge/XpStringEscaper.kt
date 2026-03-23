package io.github.magisk317.relay.xp

import io.github.magisk317.relay.common.utils.StringUtils

object XpStringEscaper {
    fun escape(value: String?): String? = StringUtils.escape(value)
}
