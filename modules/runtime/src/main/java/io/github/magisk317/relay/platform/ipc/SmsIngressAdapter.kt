package io.github.magisk317.relay.platform.ipc

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.android.sms.SmsCodeUtils
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.smscode.domain.utils.SmsCodeParsedMetadataResolver
import io.github.magisk317.xposed.logging.MagiskOtel

object SmsIngressAdapter {
    private const val NANOS_PER_MILLI = 1_000_000L

    data class Result(
        val smsMsg: SmsMsg,
        val payload: ForwardBroadcastPayload,
        val messageType: MessageType,
    )

    suspend fun toPayload(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        sourceIntent: Intent? = null,
        eventId: String? = null,
        smsCodeParser: (suspend (Context, String) -> String)? = null,
    ): Result? {
        val startedAt = System.nanoTime()
        val sender = smsMsg.sender
        val body = smsMsg.body
        if (sender.isNullOrBlank() || body.isNullOrBlank()) {
            MagiskOtel.event(
                name = "sms.ingest",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to elapsedMs(startedAt).toString(),
                    "process" to "hook",
                    "stage" to "sms_ingress",
                    "reason" to "blank_sender_or_body",
                ),
                statusOk = true,
            )
            return null
        }

        val smsCode = if (smsCodeParser == null) {
            SmsCodeUtils.parseSmsCodeIfExists(pluginContext, body, null)
        } else {
            smsCodeParser(pluginContext, body)
        }
        val messageType = if (smsCode.isBlank()) MessageType.SMS_PLAIN else MessageType.SMS_CODE
        val resolvedSmsMsg = enrichSmsMsg(
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            smsCode = smsCode,
        )
        MagiskOtel.event(
            name = "sms.ingest",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to elapsedMs(startedAt).toString(),
                "process" to "hook",
                "stage" to "sms_ingress",
                "reason" to "prepared",
                "msg_type" to messageType.name,
                "code_length" to smsCode.length.toString(),
                "body_length" to body.length.toString(),
                "company_present" to (!resolvedSmsMsg.company.isNullOrBlank()).toString(),
                "event_id_present" to (!eventId.isNullOrBlank()).toString(),
            ),
            statusOk = true,
        )

        return Result(
            smsMsg = resolvedSmsMsg,
            payload = ForwardPayloadFactory.smsPayload(
                smsMsg = resolvedSmsMsg,
                eventId = eventId,
                sourceIntent = sourceIntent,
            ),
            messageType = messageType,
        )
    }

    private fun elapsedMs(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0L)

    fun enrichSmsMsg(
        phoneContext: Context,
        smsMsg: SmsMsg,
        smsCode: String?,
    ): SmsMsg {
        val body = smsMsg.body.orEmpty()
        val (company, packageName) = resolveCompanyAndPackage(phoneContext, body, smsCode)
        return smsMsg.copy(
            date = smsMsg.date.takeIf { it > 0L } ?: System.currentTimeMillis(),
            company = company,
            smsCode = smsCode,
            packageName = packageName,
        )
    }

    internal fun resolveCompanyAndPackage(
        context: Context,
        body: String,
        smsCode: String?,
    ): Pair<String?, String?> {
        if (smsCode.isNullOrBlank()) return "" to null
        val metadata = SmsCodeParsedMetadataResolver.resolve(
            body = body,
            parseCompanyCandidates = SmsCodeUtils::parseCompanyCandidates,
            parseCompany = SmsCodeUtils::parseCompany,
            findPackageNameByLabel = { label ->
                SmsCodeUtils.findPackageNameByLabel(context, label)
            },
        )
        return metadata.company to metadata.packageName
    }
}
