package com.github.magisk317.smscode.xp.hook.code.helper

import com.github.magisk317.smscode.common.utils.XLog

object InputHelper {

    @JvmStatic
    fun sendText(
        context: android.content.Context,
        text: String?,
        autoEnter: Boolean = false,
        inputIntervalMs: Long = 0L,
    ) {
        if (text == null) return
        val intent = android.content.Intent(
            com.github.magisk317.smscode.xp.hook.system.SystemInputInjectorHook.ACTION_AUTO_INPUT,
        )
        intent.putExtra("code", text)
        intent.putExtra("autoEnter", autoEnter)
        intent.putExtra("inputIntervalMs", inputIntervalMs)
        // Broadcast without explicit package to avoid dropping delivery when
        // system_server receiver isn't bound to package.
        context.sendBroadcast(intent)
        XLog.i(
            "Sent Broadcast ACTION_AUTO_INPUT with code: %s, autoEnter: %s, inputIntervalMs: %d",
            text,
            autoEnter,
            inputIntervalMs,
        )
    }
}
