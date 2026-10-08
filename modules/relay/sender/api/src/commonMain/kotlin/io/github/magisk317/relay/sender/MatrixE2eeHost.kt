package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.MatrixSetting

/** Host services used by the product-owned Matrix E2EE implementation. */
interface MatrixE2eeHost {
    suspend fun getAuthToken(setting: MatrixSetting): String

    suspend fun sendPlaintext(setting: MatrixSetting, msgInfo: MsgInfo)

    fun buildFormattedBody(title: String, content: String): String

    fun executeGet(setting: MatrixSetting, url: String, accessToken: String): MatrixE2eeHttpResponse

    fun debug(tag: String, message: String)

    fun info(tag: String, message: String)

    fun warn(tag: String, message: String, throwable: Throwable? = null)

    fun error(tag: String, message: String, throwable: Throwable? = null)
}

data class MatrixE2eeHttpResponse(
    val code: Int,
    val body: String,
    val isSuccessful: Boolean,
)

object MatrixE2eeHostProvider {
    @Volatile
    private var instance: MatrixE2eeHost? = null

    fun install(host: MatrixE2eeHost) {
        instance = host
    }

    fun getOrNull(): MatrixE2eeHost? = instance

    fun require(): MatrixE2eeHost = checkNotNull(instance) {
        "Matrix E2EE host is not installed"
    }
}
