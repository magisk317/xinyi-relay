package io.github.magisk317.relay.sender

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import io.github.magisk317.relay.sender.config.SmsSetting
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import io.github.magisk317.xposed.logging.MagiskOtel

internal object SmsSendBackend {
    private const val SENT_RESULT_TIMEOUT_MS = 60_000L
    private const val EXTRA_TARGET_INDEX = "target_index"
    private const val EXTRA_PART_INDEX = "part_index"

    suspend fun send(
        context: Context,
        setting: SmsSetting,
        mobiles: List<String>,
        content: String,
        waitForSentResult: Boolean,
    ) {
        val startedAt = System.nanoTime()
        fun emit(result: String, reason: String, statusOk: Boolean = true) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            MagiskOtel.event(
                name = "sms.forward",
                attributes = mapOf(
                    "result" to result,
                    "duration_ms" to durationMs.toString(),
                    "process" to "app",
                    "stage" to "sms_send_backend",
                    "reason" to reason,
                    "sender_type" to "sms",
                    "target_count" to mobiles.size.toString(),
                    "content_length" to content.length.toString(),
                    "wait_for_result" to waitForSentResult.toString(),
                    "sim_slot" to setting.simSlot.toString(),
                ),
                statusOk = statusOk,
            )
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            emit(result = "error", reason = "missing_send_sms", statusOk = false)
            throw SecurityException("缺少 SEND_SMS 权限")
        }

        try {
            val smsManager = getSmsManager(context, setting.simSlot)
            if (waitForSentResult) {
                sendAndAwaitSentResult(
                    context = context.applicationContext,
                    smsManager = smsManager,
                    mobiles = mobiles,
                    content = content,
                )
            } else {
                mobiles.forEach { mobile ->
                    val parts = smsManager.divideMessage(content).ifEmpty { arrayListOf(content) }
                    smsManager.sendMultipartTextMessage(mobile, null, parts, null, null)
                }
            }
            emit(result = "ok", reason = if (waitForSentResult) "sent_confirmed" else "sent_fire_and_forget")
        } catch (error: TimeoutCancellationException) {
            emit(result = "error", reason = "timeout", statusOk = false)
            throw error
        } catch (error: SecurityException) {
            emit(result = "error", reason = "security", statusOk = false)
            throw error
        } catch (error: IllegalArgumentException) {
            emit(result = "error", reason = "invalid_sim", statusOk = false)
            throw error
        } catch (error: IllegalStateException) {
            emit(result = "error", reason = "service_unavailable", statusOk = false)
            throw error
        } catch (error: Exception) {
            emit(result = "error", reason = error.javaClass.simpleName, statusOk = false)
            throw error
        }
    }

    @SuppressLint("MissingPermission")
    private fun getSmsManager(context: Context, simSlot: Int): SmsManager {
        if (simSlot > 0) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("缺少 READ_PHONE_STATE 权限，无法指定 SIM")
            }
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                ?: throw IllegalStateException("系统 SIM 管理服务不可用")
            val targetInfo = subscriptionManager.activeSubscriptionInfoList
                ?.find { it.simSlotIndex == simSlot - 1 }
                ?: throw IllegalArgumentException("未找到 SIM 卡槽 $simSlot")
            return SmsManagerCompat.forSubscriptionId(context, targetInfo.subscriptionId)
                ?: throw IllegalStateException("系统短信服务不可用")
        }

        return SmsManagerCompat.default(context)
    }

    private suspend fun sendAndAwaitSentResult(
        context: Context,
        smsManager: SmsManager,
        mobiles: List<String>,
        content: String,
    ) {
        val action = "${context.packageName}.SMS_SENT.${System.nanoTime()}"
        val resultChannel = Channel<SmsPartResult>(Channel.UNLIMITED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action != action) return
                resultChannel.trySend(
                    SmsPartResult(
                        targetIndex = intent.getIntExtra(EXTRA_TARGET_INDEX, -1),
                        partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1),
                        resultCode = resultCode,
                    ),
                )
            }
        }

        val partsByTarget = mobiles.map {
            smsManager.divideMessage(content).ifEmpty { arrayListOf(content) }
        }
        val expectedPartCount = partsByTarget.sumOf { it.size }
        val failures = mutableListOf<SmsPartResult>()
        var receivedPartCount = 0

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(action),
            ContextCompat.RECEIVER_EXPORTED,
        )
        try {
            mobiles.forEachIndexed { targetIndex, mobile ->
                val parts = partsByTarget[targetIndex]
                val sentIntents = ArrayList(parts.mapIndexed { partIndex, _ ->
                    PendingIntent.getBroadcast(
                        context,
                        action.hashCode() + targetIndex * 1000 + partIndex,
                        Intent(action)
                            .setPackage(context.packageName)
                            .putExtra(EXTRA_TARGET_INDEX, targetIndex)
                            .putExtra(EXTRA_PART_INDEX, partIndex),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                })
                smsManager.sendMultipartTextMessage(mobile, null, parts, sentIntents, null)
            }

            try {
                withTimeout(SENT_RESULT_TIMEOUT_MS) {
                    while (receivedPartCount < expectedPartCount) {
                        val result = resultChannel.receive()
                        receivedPartCount += 1
                        if (result.resultCode != Activity.RESULT_OK) {
                            failures += result
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                throw IllegalStateException(
                    "SMS sent callback timeout: $receivedPartCount/$expectedPartCount parts reported",
                    e,
                )
            }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
            resultChannel.close()
        }

        if (failures.isNotEmpty()) {
            val failureSummary = failures.joinToString("; ") { result ->
                val target = mobiles.getOrNull(result.targetIndex) ?: "#${result.targetIndex}"
                "target=$target part=${result.partIndex + 1} result=${smsResultMessage(result.resultCode)}"
            }
            throw IllegalStateException("SMS sent callback failed: $failureSummary")
        }
    }

    private fun smsResultMessage(resultCode: Int): String {
        return when (resultCode) {
            Activity.RESULT_OK -> "OK"
            Activity.RESULT_CANCELED -> "canceled or blocked"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic failure"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "no service"
            SmsManager.RESULT_ERROR_NULL_PDU -> "null PDU"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off"
            else -> "code=$resultCode"
        }
    }

    private data class SmsPartResult(
        val targetIndex: Int,
        val partIndex: Int,
        val resultCode: Int,
    )
}
