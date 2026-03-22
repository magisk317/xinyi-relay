package io.github.magisk317.relay.xp

import android.content.Context
import io.github.magisk317.relay.common.utils.SmsCodeUtils

object XpSmsCodeParser {
    suspend fun parseSmsCodeIfExists(context: Context, content: String): String {
        return SmsCodeUtils.parseSmsCodeIfExists(context, content)
    }
}
