package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.MatrixSetting

/** Variant bridge; the product implementation lives in :relay:matrix-e2ee. */
object MatrixE2eeUtils {
    suspend fun sendMsg(context: Context, setting: MatrixSetting, msgInfo: MsgInfo) {
        MatrixE2eeSenderProvider.getOrNull()?.sendMsg(context, setting, msgInfo)
            ?: MatrixUtils.sendMsg(setting, msgInfo)
    }
}
