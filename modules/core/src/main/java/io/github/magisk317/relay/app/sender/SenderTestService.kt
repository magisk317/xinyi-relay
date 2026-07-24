package io.github.magisk317.relay.app.sender

import android.content.Context
import io.github.magisk317.relay.engine.model.MsgInfo
import io.github.magisk317.relay.engine.model.Sender
import io.github.magisk317.relay.engine.service.SenderDispatchResult
import io.github.magisk317.relay.engine.service.SenderRuntimeServiceRegistry
import io.github.magisk317.xposed.logging.MagiskOtel

class SenderTestService(context: Context) {
    private val appContext = context.applicationContext

    suspend fun sendTestSender(sender: Sender, msgInfo: MsgInfo): SenderDispatchResult {
        val startedAt = System.nanoTime()
        val dispatcher = SenderRuntimeServiceRegistry.requireInstalled().createDispatcher(appContext)
        return try {
            val result = dispatcher.dispatchToSender(
                sender = sender,
                msgInfo = msgInfo,
                traceId = TEST_TRACE_ID,
            )
            if (!result.success) {
                MagiskOtel.event(
                    name = "sms.forward",
                    attributes = mapOf(
                        "result" to "error",
                        "duration_ms" to elapsedMs(startedAt).toString(),
                        "process" to "app",
                        "stage" to "sender_test",
                        "reason" to "dispatch_failed",
                        "sender_type" to sender.type.toString(),
                    ),
                    statusOk = false,
                )
                throw SenderTestException(result.message.ifBlank { "Sender test failed" })
            }
            MagiskOtel.event(
                name = "sms.forward",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to elapsedMs(startedAt).toString(),
                    "process" to "app",
                    "stage" to "sender_test",
                    "sender_type" to sender.type.toString(),
                ),
                statusOk = true,
            )
            result
        } catch (error: SenderTestException) {
            throw error
        } catch (error: Throwable) {
            MagiskOtel.event(
                name = "sms.forward",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to elapsedMs(startedAt).toString(),
                    "process" to "app",
                    "stage" to "sender_test",
                    "reason" to "exception",
                    "error_class" to error.javaClass.simpleName,
                    "sender_type" to sender.type.toString(),
                ),
                statusOk = false,
            )
            throw error
        }
    }

    suspend fun sendScheduledSms(
        simSlot: Int,
        mobiles: String,
        msgInfo: MsgInfo,
        waitForSentResult: Boolean = true,
    ) {
        val startedAt = System.nanoTime()
        try {
            SenderRuntimeServiceRegistry.requireInstalled().scheduledSmsSender.sendSms(
                context = appContext,
                simSlot = simSlot,
                mobiles = mobiles,
                msgInfo = msgInfo,
                waitForSentResult = waitForSentResult,
            )
            MagiskOtel.event(
                name = "sms.forward",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to elapsedMs(startedAt).toString(),
                    "process" to "app",
                    "stage" to "sender_test_sms",
                    "reason" to "scheduled_sms",
                    "wait_for_sent" to waitForSentResult.toString(),
                ),
                statusOk = true,
            )
        } catch (error: Throwable) {
            MagiskOtel.event(
                name = "sms.forward",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to elapsedMs(startedAt).toString(),
                    "process" to "app",
                    "stage" to "sender_test_sms",
                    "reason" to "exception",
                    "error_class" to error.javaClass.simpleName,
                ),
                statusOk = false,
            )
            throw error
        }
    }

    private fun elapsedMs(startedAt: Long): Long {
        return ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
    }

    companion object {
        private const val TEST_TRACE_ID = "ui-sender-test"
    }
}

class SenderTestException(message: String) : RuntimeException(message)
