package io.github.magisk317.relay.platform.ipc

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.contract.constant.MessageType
import io.github.magisk317.relay.android.sms.SmsCodeUtils
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import io.github.magisk317.smscode.domain.utils.SmsCodeParsedMetadataResolver

object SmsIngressAdapter {
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
        val sender = smsMsg.sender
        val body = smsMsg.body
        if (sender.isNullOrBlank() || body.isNullOrBlank()) {
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
