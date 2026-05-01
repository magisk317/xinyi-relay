package io.github.magisk317.relay.xpbridge

import android.content.Context
import io.github.magisk317.relay.android.sms.SmsCodeUtils

object XpSmsCodeParser {
    suspend fun parseSmsCodeIfExists(context: Context, content: String): String {
        return SmsCodeUtils.parseSmsCodeIfExists(context, content)
    }
}
