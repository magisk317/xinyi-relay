package io.github.magisk317.relay.matrix.e2ee

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.MatrixE2eeHostProvider
import io.github.magisk317.relay.sender.MatrixE2eeHttpResponse
import io.github.magisk317.relay.sender.config.MatrixSetting

internal object MatrixUtils {
    suspend fun getAuthToken(setting: MatrixSetting): String =
        MatrixE2eeHostProvider.require().getAuthToken(setting)

    suspend fun sendMsg(setting: MatrixSetting, msgInfo: MsgInfo) {
        MatrixE2eeHostProvider.require().sendPlaintext(setting, msgInfo)
    }

    fun buildFormattedBody(title: String, content: String): String =
        MatrixE2eeHostProvider.require().buildFormattedBody(title, content)

    fun executeGet(setting: MatrixSetting, url: String, accessToken: String): MatrixE2eeHttpResponse =
        MatrixE2eeHostProvider.require().executeGet(setting, url, accessToken)
}

internal object SLog {
    fun d(tag: String, message: String) = MatrixE2eeHostProvider.getOrNull()?.debug(tag, message)

    fun i(tag: String, message: String) = MatrixE2eeHostProvider.getOrNull()?.info(tag, message)

    fun w(tag: String, message: String) = MatrixE2eeHostProvider.getOrNull()?.warn(tag, message)

    fun w(tag: String, message: String, throwable: Throwable) =
        MatrixE2eeHostProvider.getOrNull()?.warn(tag, message, throwable)

    fun e(tag: String, message: String) = MatrixE2eeHostProvider.getOrNull()?.error(tag, message)

    fun e(tag: String, message: String, throwable: Throwable) =
        MatrixE2eeHostProvider.getOrNull()?.error(tag, message, throwable)
}
