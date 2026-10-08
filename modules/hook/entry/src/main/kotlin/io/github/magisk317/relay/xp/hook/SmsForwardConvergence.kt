package io.github.magisk317.relay.xp.hook

import android.content.Intent

internal const val EXTRA_PARSED_SMS_FORWARD_DISPATCHED =
    "io.github.magisk317.relay.extra.parsed_sms_forward_dispatched"

internal object SmsForwardConvergence {
    fun markParsedSmsForwardDispatched(intent: Intent) {
        intent.putExtra(EXTRA_PARSED_SMS_FORWARD_DISPATCHED, true)
    }

    fun wasParsedSmsForwardDispatched(intent: Intent): Boolean {
        return intent.getBooleanExtra(EXTRA_PARSED_SMS_FORWARD_DISPATCHED, false)
    }
}
