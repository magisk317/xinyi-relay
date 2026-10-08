package io.github.magisk317.relay.sender

import io.github.magisk317.xposed.logging.MagiskOtel

internal object SenderTelemetry {
    @Suppress("TooGenericExceptionCaught")
    suspend fun <T> trace(
        senderType: String,
        stage: String,
        block: suspend () -> T,
    ): T {
        val startedAt = System.nanoTime()
        return try {
            block().also {
                emitForward(
                    senderType = senderType,
                    stage = stage,
                    result = "ok",
                    reason = "success",
                    durationMs = elapsedMillis(startedAt),
                )
            }
        } catch (error: Exception) {
            emitForward(
                senderType = senderType,
                stage = stage,
                result = "error",
                reason = error.javaClass.simpleName,
                durationMs = elapsedMillis(startedAt),
                statusOk = false,
            )
            throw error
        }
    }

    private fun emitForward(
        senderType: String,
        stage: String,
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
                "stage" to stage,
                "reason" to reason,
                "sender_type" to senderType,
            ),
            statusOk = statusOk,
        )
    }

    private fun elapsedMillis(startedAt: Long): Long {
        return ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0L)
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}
