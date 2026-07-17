package io.github.magisk317.relay.platform.ipc

import android.content.Intent
import io.github.magisk317.relay.android.common.utils.XLog
import io.github.magisk317.relay.android.data.db.entity.SmsMsg
import java.util.UUID
import kotlin.math.abs

object ForwardPayloadFactory {
    private const val EXTRA_MMS_FROM = "from"
    private const val EXTRA_MMS_ADDRESS = "address"
    private const val EXTRA_MMS_SUBJECT = "subject"
    private const val EXTRA_MMS_CONTENT_LOCATION = "content_location"
    private const val EXTRA_MMS_CONTENT_LOCATION_CAMEL = "contentLocation"
    private const val EXTRA_MMS_TRANSACTION_ID = "transaction_id"
    private const val EXTRA_MMS_TRANSACTION_ID_CAMEL = "transactionId"
    private const val EXTRA_MMS_DATA = "data"
    private const val MMS_SENDER_FALLBACK = "MMS"
    private const val MMS_PDU_UNAVAILABLE_FALLBACK = "MMS received (PDU data unavailable)"
    private const val MMS_METADATA_UNAVAILABLE_FALLBACK = "MMS received (PDU metadata unavailable)"
    private const val EVENT_ID_HASH_RADIX = 36

    fun ensureSmsEventId(intent: Intent): String {
        val existing = intent.getStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID).orEmpty().trim()
        if (existing.isNotEmpty()) {
            return existing
        }
        val generated = stableSmsEventId(
            stableParts = listOfNotNull(intent.smsPduHash()),
            fallbackSeed = abs(intent.hashCode()).toString(EVENT_ID_HASH_RADIX),
        )
        intent.putExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, generated)
        return generated
    }

    fun ensureSmsEventId(intent: Intent, smsMsg: SmsMsg): String {
        val existing = intent.getStringExtra(ForwardBroadcastContract.EXTRA_EVENT_ID).orEmpty().trim()
        if (existing.isNotEmpty()) {
            return existing
        }
        val pduHash = intent.smsPduHash()
        val generated = stableSmsEventId(
            stableParts = stableSmsEventParts(smsMsg, pduHash),
            fallbackSeed = abs(intent.hashCode()).toString(EVENT_ID_HASH_RADIX),
        )
        intent.putExtra(ForwardBroadcastContract.EXTRA_EVENT_ID, generated)
        return generated
    }

    fun smsPayload(
        smsMsg: SmsMsg,
        eventId: String? = null,
        sourceIntent: Intent? = null,
    ): ForwardBroadcastPayload {
        val resolvedEventId = eventId?.takeIf { it.isNotBlank() }
            ?: stableSmsEventId(
                stableParts = stableSmsEventParts(smsMsg, sourceIntent?.smsPduHash()),
                fallbackSeed = (smsMsg.sender ?: "") + (smsMsg.body ?: "") + smsMsg.date,
            )
        return ForwardBroadcastPayload(
            sender = smsMsg.sender,
            body = smsMsg.body,
            date = smsMsg.date,
            company = smsMsg.company,
            smsCode = smsMsg.smsCode,
            packageName = smsMsg.packageName,
            notifyChannelId = smsMsg.notifyChannelId,
            msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
            forwardSource = ForwardBroadcastContract.SOURCE_SMS_HOOK,
            eventId = resolvedEventId,
        ).withSimRoutingFrom(sourceIntent)
    }

    fun mmsPayload(intent: Intent, receivedAt: Long = System.currentTimeMillis()): ForwardBroadcastPayload {
        val rawPdu = intent.getByteArrayExtra(EXTRA_MMS_DATA)
        val rawPduHash = rawPdu?.takeIf { it.isNotEmpty() }?.contentHashCode()?.toString(EVENT_ID_HASH_RADIX)
        val parseResult = rawPdu?.takeIf { it.isNotEmpty() }?.let(MmsNotificationPduParser::parse)
        val parsedMms = (parseResult as? MmsNotificationPduParser.Result.Parsed)?.metadata
        logMmsParseDiagnostics(parseResult)
        val sender = intent.firstNonBlankStringExtra(
            EXTRA_MMS_FROM,
            EXTRA_MMS_ADDRESS,
            ForwardBroadcastContract.EXTRA_SENDER,
        ) ?: parsedMms?.sender ?: MMS_SENDER_FALLBACK
        val subject = intent.firstNonBlankStringExtra(EXTRA_MMS_SUBJECT) ?: parsedMms?.subject
        val contentLocation = intent.firstNonBlankStringExtra(
            EXTRA_MMS_CONTENT_LOCATION,
            EXTRA_MMS_CONTENT_LOCATION_CAMEL,
        ) ?: parsedMms?.contentLocation
        val transactionId = intent.firstNonBlankStringExtra(
            EXTRA_MMS_TRANSACTION_ID,
            EXTRA_MMS_TRANSACTION_ID_CAMEL,
        ) ?: parsedMms?.transactionId
        val body = (listOfNotNull(subject) + listOfNotNull(
            contentLocation,
            transactionId,
        ))
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "\n")
            ?: if (rawPdu == null || rawPdu.isEmpty()) {
                MMS_PDU_UNAVAILABLE_FALLBACK
            } else {
                MMS_METADATA_UNAVAILABLE_FALLBACK
            }
        val stableEventParts = listOfNotNull(
            sender.takeUnless { it == MMS_SENDER_FALLBACK },
            subject,
            contentLocation,
            transactionId,
            rawPduHash,
        )
        val eventId = stableMmsEventId(stableEventParts, fallbackSeed = receivedAt.toString())
        return ForwardBroadcastPayload(
            sender = sender,
            body = body,
            date = receivedAt,
            company = MMS_SENDER_FALLBACK,
            smsCode = null,
            packageName = null,
            notifyChannelId = "",
            msgType = ForwardBroadcastContract.MSG_TYPE_SMS,
            forwardSource = ForwardBroadcastContract.SOURCE_SMS_HOOK,
            eventId = eventId,
        ).withSimRoutingFrom(intent)
    }

    private fun logMmsParseDiagnostics(result: MmsNotificationPduParser.Result?) {
        when (result) {
            is MmsNotificationPduParser.Result.Parsed -> {
                if (!result.isComplete || result.warnings.isNotEmpty()) {
                    val missing = result.missingMandatoryHeaders.joinToString(",") { it.wireName }
                        .ifEmpty { "none" }
                    val warnings = result.warnings.joinToString(",") { it.name.lowercase() }
                        .ifEmpty { "none" }
                    XLog.w(
                        "Standard MMS PDU parsed with diagnostics: missing=%s warnings=%s",
                        missing,
                        warnings,
                    )
                }
            }
            is MmsNotificationPduParser.Result.Unsupported -> XLog.w(
                "Standard MMS PDU type unsupported: messageType=0x%s; using intent metadata or safe fallback",
                result.messageType.toString(16),
            )
            is MmsNotificationPduParser.Result.Malformed -> XLog.w(
                "Standard MMS PDU malformed: reason=%s; using intent metadata or safe fallback",
                result.reason.name.lowercase(),
            )
            null -> Unit
        }
    }

    private fun stableMmsEventId(stableParts: List<String>, fallbackSeed: String): String {
        if (stableParts.isEmpty()) {
            return ForwardBroadcastContract.buildEventId("mms", fallbackSeed)
        }
        return "mms_${Integer.toUnsignedString(stableParts.joinToString("|").hashCode(), EVENT_ID_HASH_RADIX)}"
    }

    private fun stableSmsEventId(stableParts: List<String>, fallbackSeed: String): String {
        val seed = stableParts.takeIf { it.isNotEmpty() }?.joinToString("|") ?: fallbackSeed
        return "sms_${Integer.toUnsignedString(seed.hashCode(), EVENT_ID_HASH_RADIX)}"
    }

    private fun SmsMsg.stableSmsEventParts(): List<String> {
        return listOfNotNull(
            sender?.trim()?.ifBlank { null },
            body?.trim()?.ifBlank { null },
            date.takeIf { it > 0L }?.toString(),
        )
    }

    private fun stableSmsEventParts(smsMsg: SmsMsg, pduHash: String?): List<String> {
        return listOfNotNull(pduHash) + if (pduHash == null) smsMsg.stableSmsEventParts() else emptyList()
    }

    @Suppress("DEPRECATION")
    private fun Intent.smsPduHash(): String? {
        val rawPdus = extras?.get("pdus") as? Array<*> ?: return null
        val pduHashes = rawPdus.mapNotNull { (it as? ByteArray)?.contentHashCode()?.toString(EVENT_ID_HASH_RADIX) }
        if (pduHashes.isEmpty() || pduHashes.size != rawPdus.size) return null
        return pduHashes.joinToString(separator = "_")
    }

    private fun Intent.firstNonBlankStringExtra(vararg names: String): String? {
        return names.firstNotNullOfOrNull { name ->
            getStringExtra(name)?.trim()?.ifBlank { null }
        }
    }

    fun appNotificationPayload(
        packageName: String,
        title: String,
        body: String,
        timestamp: Long,
        appName: String,
        notifyChannelId: String,
        smsCode: String? = null,
    ): ForwardBroadcastPayload {
        return ForwardBroadcastPayload(
            sender = title,
            body = body,
            date = timestamp,
            company = appName,
            smsCode = smsCode,
            packageName = packageName,
            notifyChannelId = notifyChannelId,
            msgType = ForwardBroadcastContract.MSG_TYPE_APP_NOTIFY,
            forwardSource = ForwardBroadcastContract.SOURCE_NOTIFICATION_LISTENER,
            eventId = ForwardBroadcastContract.buildEventId("nls", packageName),
        )
    }

    fun callPayload(
        packageName: String,
        sender: String,
        body: String,
        company: String,
        timestamp: Long,
        callType: Int,
        callStage: String,
    ): ForwardBroadcastPayload {
        return ForwardBroadcastPayload(
            sender = sender,
            body = body,
            date = timestamp,
            company = company,
            smsCode = null,
            packageName = packageName,
            notifyChannelId = "",
            msgType = ForwardBroadcastContract.MSG_TYPE_CALL_NOTIFY,
            forwardSource = ForwardBroadcastContract.SOURCE_TELEPHONY_STATE,
            eventId = "tel_${UUID.randomUUID().toString().take(8)}",
            callType = callType,
            callStage = callStage,
        )
    }
}
