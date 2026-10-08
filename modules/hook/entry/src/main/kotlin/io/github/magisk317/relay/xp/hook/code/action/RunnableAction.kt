package io.github.magisk317.relay.xp.hook.code.action

import android.content.Context
import io.github.magisk317.relay.xpbridge.SmsMsg

/**
 * Runnable + Action + Callable
 */
abstract class RunnableAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg),
    Runnable {

    override fun run() {
        call()
    }
}
