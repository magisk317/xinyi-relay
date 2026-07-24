package io.github.magisk317.relay.sender

import android.content.Context
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.sender.config.SmsSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.magisk317.xposed.logging.MagiskOtel

object SmsUtils {
    private const val TAG = "SmsUtils"

    private fun emitForward(
        result: String,
        reason: String,
        durationMs: Long,
        statusOk: Boolean = true,
    ) {
        MagiskOtel.event(
            name = "sms.forward",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "app",
                "stage" to "sms_utils_send",
                "reason" to reason,
                "sender_type" to "sms",
            ),
            statusOk = statusOk,
        )
    }


    suspend fun sendMsg(
        context: Context,
        setting: SmsSetting,
        msgInfo: MsgInfo,
        waitForSentResult: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        try {

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
    
            emitForward(
                result = "ok",
                reason = "success",
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
            )
        } catch (error: Exception) {
            emitForward(
                result = "error",
                reason = error.javaClass.simpleName,
                durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L),
                statusOk = false,
            )
            throw error
        }
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
