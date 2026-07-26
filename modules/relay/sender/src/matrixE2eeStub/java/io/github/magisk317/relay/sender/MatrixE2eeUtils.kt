package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.MatrixSetting

object MatrixE2eeUtils {
    @Suppress("UNUSED_PARAMETER")
    suspend fun sendMsg(context: Context, setting: MatrixSetting, msgInfo: MsgInfo) {
        MatrixE2eeSenderProvider.getOrNull()?.let { sender ->
            sender.sendMsg(context, setting, msgInfo)
            return
        }
        MatrixUtils.sendMsg(setting, msgInfo)
    }
}
