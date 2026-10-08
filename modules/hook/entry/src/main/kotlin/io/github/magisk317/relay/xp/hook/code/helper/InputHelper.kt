package io.github.magisk317.relay.xp.hook.code.helper

import android.content.Context
import io.github.magisk317.smscode.runtime.contract.autoinput.AutoInputBroadcastContract
import io.github.magisk317.smscode.xposed.helper.LocalInputActions
import io.github.magisk317.smscode.xposed.prefs.CorePrefs

/** Thin delegate; implementation lives in core [LocalInputActions]. */
object InputHelper {

    @JvmStatic
    fun sendText(
        context: Context,
        text: String?,
        autoEnter: Boolean = false,
        inputIntervalMs: Long = 0L,
        attemptId: Long? = null,
    ) {
        LocalInputActions.sendText(
            context = context,
            text = text,
            autoEnter = autoEnter,
            inputIntervalMs = inputIntervalMs,
            attemptId = attemptId,
            tokenProvider = {
                CorePrefs.getString(AutoInputBroadcastContract.EXTRA_IPC_TOKEN, "")
            },
        )
    }

    @JvmStatic
    fun sendToast(
        context: Context,
        text: String?,
        duration: Int = android.widget.Toast.LENGTH_LONG,
    ) {
        LocalInputActions.sendToast(context, text, duration)
    }
}
