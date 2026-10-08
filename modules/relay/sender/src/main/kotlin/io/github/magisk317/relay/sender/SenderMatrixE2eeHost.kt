package io.github.magisk317.relay.sender

import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.MatrixSetting
import okhttp3.Request

/** Connects the shared E2EE runtime to sender-owned auth, fallback, and logging policies. */
object SenderMatrixE2eeHost : MatrixE2eeHost {
    override suspend fun getAuthToken(setting: MatrixSetting): String =
        MatrixUtils.getAuthToken(setting)

    override suspend fun sendPlaintext(setting: MatrixSetting, msgInfo: MsgInfo) {
        MatrixUtils.sendMsg(setting, msgInfo)
    }

    override fun buildFormattedBody(title: String, content: String): String =
        MatrixUtils.buildFormattedBody(title, content)

    override fun executeGet(
        setting: MatrixSetting,
        url: String,
        accessToken: String,
    ): MatrixE2eeHttpResponse {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $accessToken")
            .build()
        return MatrixUtils.buildClient(setting).newCall(request).execute().use { response ->
            MatrixE2eeHttpResponse(
                code = response.code,
                body = response.body.string(),
                isSuccessful = response.isSuccessful,
            )
        }
    }

    override fun debug(tag: String, message: String) = SLog.d(tag, message)

    override fun info(tag: String, message: String) = SLog.i(tag, message)

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) SLog.w(tag, message) else SLog.w(tag, message, throwable)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) SLog.e(tag, message) else SLog.e(tag, message, throwable)
    }
}
