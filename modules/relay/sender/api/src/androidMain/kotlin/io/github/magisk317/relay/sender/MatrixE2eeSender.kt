package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.MatrixSetting

interface MatrixE2eeSender {
    suspend fun sendMsg(context: Context, setting: MatrixSetting, msgInfo: MsgInfo)
}

object MatrixE2eeSenderProvider {
    @Volatile
    private var instance: MatrixE2eeSender? = null

    fun install(sender: MatrixE2eeSender) {
        instance = sender
    }

    /**
     * Drop the installed sender so [io.github.magisk317.relay.sender.MatrixE2eeUtils]
     * falls back to plaintext delivery until a plugin installs a new one.
     */
    fun uninstall() {
        instance = null
    }

    fun getOrNull(): MatrixE2eeSender? = instance

    val isInstalled: Boolean get() = instance != null
}
