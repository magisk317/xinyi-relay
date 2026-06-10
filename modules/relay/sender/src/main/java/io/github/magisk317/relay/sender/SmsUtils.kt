package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.SmsSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsUtils {
    private const val TAG = "SmsUtils"

    suspend fun sendMsg(
        context: Context,
        setting: SmsSetting,
        msgInfo: MsgInfo,
        waitForSentResult: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val mobiles = normalizeTargetMobiles(setting.mobiles, msgInfo.from)
        if (mobiles.isEmpty()) {
            SLog.e(TAG, "No target mobile configured")
            throw IllegalArgumentException("未配置目标手机号")
        }

        runCatching {
            SmsSendBackend.send(
                context = context,
                setting = setting,
                mobiles = mobiles,
                content = msgInfo.content,
                waitForSentResult = waitForSentResult,
            )
            SLog.i(TAG, "SMS send success, targets=${mobiles.size}")
        }.onFailure {
            SLog.e(TAG, "SMS send failed", it)
        }.getOrElse { throw it }
    }

    internal fun normalizeTargetMobiles(rawMobiles: String, sourceNumber: String?): List<String> {
        return rawMobiles
            .replace("[from]", sourceNumber.orEmpty())
            .replace("{{来源号码}}", sourceNumber.orEmpty())
            .replace("[,，;；]".toRegex(), ",")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }
}
