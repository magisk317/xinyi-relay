package io.github.magisk317.relay.xp.hook.code.action

import android.content.Context
import android.os.Bundle
import io.github.magisk317.relay.xp.SmsMsg
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.Callable

/**
 * Action + Callable
 */
abstract class CallableAction(
    @JvmField protected val mPluginContext: Context,
    @JvmField protected val mPhoneContext: Context,
    @JvmField protected var mSmsMsg: SmsMsg,
) : Action<Bundle?>,
    Callable<Bundle?> {

    override fun call(): Bundle? = try {
        action()
    } catch (t: Throwable) {
        XLog.e("Error in CallableAction#call()", t)
        null
    }
}
