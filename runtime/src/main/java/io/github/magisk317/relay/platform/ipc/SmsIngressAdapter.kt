package io.github.magisk317.relay.platform.ipc

import android.content.Context
import android.content.Intent
import io.github.magisk317.relay.common.constant.MessageType
import io.github.magisk317.relay.sms.SmsCodeUtils
import io.github.magisk317.relay.data.db.entity.SmsMsg

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
    ): Result? {
        val sender = smsMsg.sender
        val body = smsMsg.body
        if (sender.isNullOrBlank() || body.isNullOrBlank()) {
            return null
        }

        val smsCode = SmsCodeUtils.parseSmsCodeIfExists(pluginContext, body, null)
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
        val candidates = SmsCodeUtils.parseCompanyCandidates(body)
            .map { normalizeCompanyLabel(it) }
            .filter { it.isNotBlank() }
        var company = normalizeCompanyLabel(SmsCodeUtils.parseCompany(body))
        var resolvedPackage: String? = null
        for (candidate in candidates) {
            val pkg = SmsCodeUtils.findPackageNameByLabel(context, candidate)
            if (!pkg.isNullOrBlank()) {
                company = candidate
                resolvedPackage = pkg
                break
            }
        }
        if (resolvedPackage.isNullOrBlank()) {
            resolvedPackage = SmsCodeUtils.findPackageNameByLabel(context, company)
        }
        return company to resolvedPackage
    }

    private fun normalizeCompanyLabel(value: String): String {
        return value.trim().trim('【', '】', '[', ']')
    }
}
