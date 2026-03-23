package io.github.magisk317.relay.sms

import android.content.Intent
import android.telephony.SmsMessage

object SmsMessageUtils {
    @JvmStatic
    fun fromIntent(intent: Intent): Array<SmsMessage> =
        io.github.magisk317.smscode.domain.utils.SmsMessageUtils.fromIntent(intent)

    @JvmStatic
    fun getMessageBody(messageParts: Array<SmsMessage>): String =
        io.github.magisk317.smscode.domain.utils.SmsMessageUtils.getMessageBody(messageParts)
}
