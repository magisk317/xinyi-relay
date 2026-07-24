package io.github.magisk317.relay.platform.ipc

import io.github.magisk317.xposed.logging.MagiskOtel

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Minimal parser for the MMS Notification.ind PDU carried by WAP_PUSH_RECEIVED.
 *
 * Standard mode runs in the ordinary application process, where Android's hidden MMS parser is
 * not part of the app class path. This parser intentionally covers only notification metadata;
 * downloading or decoding the referenced multipart message remains the default SMS app's job.
 * The bounded wire primitives correspond to WSP Value-length/Text-string/Integer-value and the
 * OMA MMS Encapsulation Notification.ind header definitions.
 */
internal object MmsNotificationPduParser {
    private const val HEADER_CONTENT_LOCATION = 0x83
    private const val HEADER_CONTENT_TYPE = 0x84
    private const val HEADER_EXPIRY = 0x88
    private const val HEADER_FROM = 0x89
    private const val HEADER_MESSAGE_CLASS = 0x8A
    private const val HEADER_MESSAGE_TYPE = 0x8C
    private const val HEADER_MMS_VERSION = 0x8D
    private const val HEADER_MESSAGE_SIZE = 0x8E
    private const val HEADER_SUBJECT = 0x96
    private const val HEADER_TRANSACTION_ID = 0x98

    private const val MESSAGE_TYPE_NOTIFICATION_IND = 0x82
    private const val FROM_ADDRESS_PRESENT = 0x80
    private const val FROM_INSERT_ADDRESS = 0x81
    private const val VALUE_ABSOLUTE = 0x80
    private const val VALUE_RELATIVE = 0x81

    private const val TEXT_HEADER_MIN = 0x20
    private const val TEXT_HEADER_MAX = 0x7F
    private const val LENGTH_QUOTE = 0x1F
    private const val SHORT_LENGTH_MAX = 0x1E
    private const val QUOTE = 0x7F
    private const val SHORT_INTEGER_FLAG = 0x80
    private const val SHORT_INTEGER_VALUE_MASK = 0x7F
    private const val OCTET_MASK = 0xFF
    private const val UINTVAR_BITS_PER_OCTET = 7
    private const val MAX_UINTVAR_OCTETS = 5
    private const val MAX_LONG_INTEGER_OCTETS = 8
    private const val MAX_HEADER_COUNT = 128
    private const val MAX_METADATA_BYTES = 8 * 1024

    data class Metadata(
        val sender: String?,
        val subject: String?,
        val contentLocation: String?,
        val transactionId: String?,
    )

    enum class MandatoryHeader(val wireName: String) {
        MMS_VERSION("mms-version"),
        TRANSACTION_ID("transaction-id"),
        MESSAGE_CLASS("message-class"),
        MESSAGE_SIZE("message-size"),
        EXPIRY("expiry"),
        CONTENT_LOCATION("content-location"),
    }

    enum class Warning {
        DUPLICATE_HEADER,
        UNSUPPORTED_CHARSET,
        INVALID_TEXT_ENCODING,
    }

    enum class MalformedReason {
        EMPTY_PDU,
        TRUNCATED,
        INVALID_HEADER,
        INVALID_LENGTH,
        INVALID_INTEGER,
        INVALID_FROM,
        INVALID_EXPIRY,
        MISSING_MESSAGE_TYPE,
        TOO_MANY_HEADERS,
        METADATA_TOO_LARGE,
    }

    sealed interface Result {
        data class Parsed(
            val metadata: Metadata,
            val missingMandatoryHeaders: Set<MandatoryHeader>,
            val warnings: Set<Warning>,
        ) : Result {
            val isComplete: Boolean
                get() = missingMandatoryHeaders.isEmpty()
        }

        data class Unsupported(val messageType: Int) : Result

        data class Malformed(val reason: MalformedReason) : Result
    }

    fun parse(data: ByteArray): Result {
        if (data.isEmpty()) {
            MagiskOtel.event(
                name = "sms.observe",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "mms_pdu_parse",
                    "reason" to "empty_pdu",
                    "payload_size" to "0",
                ),
                statusOk = false,
            )
            return Result.Malformed(MalformedReason.EMPTY_PDU)
        }

        return try {
            val cursor = Cursor(data)
            val values = ParsedValues()
            var headerCount = 0

            parseHeaders@ while (cursor.hasRemaining()) {
                if (++headerCount > MAX_HEADER_COUNT) {
                    throw MalformedPdu(MalformedReason.TOO_MANY_HEADERS)
                }

                val header = cursor.readOctet()
                if (header in TEXT_HEADER_MIN..TEXT_HEADER_MAX) {
                    cursor.rewindOneOctet()
                    cursor.readTextString()
                    cursor.skipFieldValue()
                    continue
                }

                when (header) {
                    HEADER_MESSAGE_TYPE -> values.setMessageType(cursor.readOctet())
                    HEADER_MMS_VERSION -> {
                        cursor.readShortInteger()
                        values.markPresent(MandatoryHeader.MMS_VERSION)
                    }
                    HEADER_TRANSACTION_ID -> values.setTransactionId(cursor.readTextString(values.warnings))
                    HEADER_FROM -> values.setSender(cursor.readFromValue(values.warnings))
                    HEADER_SUBJECT -> values.setSubject(cursor.readEncodedString(values.warnings))
                    HEADER_MESSAGE_CLASS -> {
                        cursor.skipFieldValue()
                        values.markPresent(MandatoryHeader.MESSAGE_CLASS)
                    }
                    HEADER_MESSAGE_SIZE -> {
                        cursor.readLongInteger()
                        values.markPresent(MandatoryHeader.MESSAGE_SIZE)
                    }
                    HEADER_EXPIRY -> {
                        cursor.readExpiryValue()
                        values.markPresent(MandatoryHeader.EXPIRY)
                    }
                    HEADER_CONTENT_LOCATION -> values.setContentLocation(cursor.readTextString(values.warnings))
                    HEADER_CONTENT_TYPE -> {
                        // Content-Type terminates MMS headers. Notification.ind normally has no
                        // body, but stopping here keeps a non-notification multipart body from
                        // being misinterpreted as more headers.
                        cursor.skipFieldValue()
                        break@parseHeaders
                    }
                    else -> {
                        if (header < SHORT_INTEGER_FLAG) {
                            throw MalformedPdu(MalformedReason.INVALID_HEADER)
                        }
                        cursor.skipFieldValue()
                    }
                }
            }

            val result = values.toResult()
            MagiskOtel.event(
                name = "sms.observe",
                attributes = mapOf(
                    "result" to when (result) {
                        is Result.Parsed -> "ok"
                        is Result.Unsupported -> "skip"
                        is Result.Malformed -> "error"
                    },
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "mms_pdu_parse",
                    "reason" to when (result) {
                        is Result.Parsed -> if (result.isComplete) "complete" else "partial"
                        is Result.Unsupported -> "unsupported"
                        is Result.Malformed -> result.reason.name.lowercase()
                    },
                    "payload_size" to data.size.toString(),
                ),
                statusOk = result is Result.Parsed,
            )
            result
        } catch (error: MalformedPdu) {
            MagiskOtel.event(
                name = "sms.observe",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "mms_pdu_parse",
                    "reason" to error.reason.name.lowercase(),
                    "payload_size" to data.size.toString(),
                ),
                statusOk = false,
            )
            Result.Malformed(error.reason)
        }
    }

    private class ParsedValues {
        var messageType: Int? = null
            private set
        private var sender: String? = null
        private var subject: String? = null
        private var contentLocation: String? = null
        private var transactionId: String? = null
        private val presentMandatoryHeaders = mutableSetOf<MandatoryHeader>()
        val warnings = mutableSetOf<Warning>()

        fun setMessageType(value: Int) {
            if (messageType != null) {
                warnings += Warning.DUPLICATE_HEADER
                return
            }
            messageType = value
        }

        fun setSender(value: String?) {
            if (sender != null) {
                warnings += Warning.DUPLICATE_HEADER
                return
            }
            sender = value?.withoutAddressType()
        }

        fun setSubject(value: String?) {
            if (subject != null) {
                warnings += Warning.DUPLICATE_HEADER
                return
            }
            subject = value
        }

        fun setContentLocation(value: String?) {
            if (contentLocation != null) {
                warnings += Warning.DUPLICATE_HEADER
                return
            }
            contentLocation = value
            if (value != null) markPresent(MandatoryHeader.CONTENT_LOCATION)
        }

        fun setTransactionId(value: String?) {
            if (transactionId != null) {
                warnings += Warning.DUPLICATE_HEADER
                return
            }
            transactionId = value
            if (value != null) markPresent(MandatoryHeader.TRANSACTION_ID)
        }

        fun markPresent(header: MandatoryHeader) {
            if (!presentMandatoryHeaders.add(header)) {
                warnings += Warning.DUPLICATE_HEADER
            }
        }

        fun toResult(): Result {
            val type = messageType ?: return Result.Malformed(MalformedReason.MISSING_MESSAGE_TYPE)
            if (type != MESSAGE_TYPE_NOTIFICATION_IND) return Result.Unsupported(type)

            return Result.Parsed(
                metadata = Metadata(
                    sender = sender,
                    subject = subject,
                    contentLocation = contentLocation,
                    transactionId = transactionId,
                ),
                missingMandatoryHeaders = MandatoryHeader.entries
                    .filterNotTo(linkedSetOf()) { it in presentMandatoryHeaders },
                warnings = warnings.toSet(),
            )
        }
    }

    private class Cursor(private val data: ByteArray) {
        private var position = 0

        fun hasRemaining(): Boolean = position < data.size

        fun rewindOneOctet() {
            if (position == 0) throw MalformedPdu(MalformedReason.INVALID_HEADER)
            position--
        }

        fun readOctet(limit: Int = data.size): Int {
            if (position >= limit || position >= data.size) {
                throw MalformedPdu(MalformedReason.TRUNCATED)
            }
            return data[position++].toInt() and OCTET_MASK
        }

        fun readShortInteger(limit: Int = data.size): Int {
            val value = readOctet(limit)
            if (value and SHORT_INTEGER_FLAG == 0) throw MalformedPdu(MalformedReason.INVALID_INTEGER)
            return value and SHORT_INTEGER_VALUE_MASK
        }

        fun readLongInteger(limit: Int = data.size): Long {
            val count = readOctet(limit)
            if (count !in 1..MAX_LONG_INTEGER_OCTETS || position + count > limit) {
                throw MalformedPdu(MalformedReason.INVALID_INTEGER)
            }
            var value = 0L
            repeat(count) {
                value = (value shl 8) or readOctet(limit).toLong()
            }
            return value
        }

        fun readTextString(
            warnings: MutableSet<Warning>? = null,
            limit: Int = data.size,
        ): String? {
            return decodeText(
                bytes = readTextStringBytes(limit),
                charset = Charsets.UTF_8,
                warnings = warnings,
            )
        }

        fun readEncodedString(warnings: MutableSet<Warning>, limit: Int = data.size): String? {
            if (peekOctet(limit) == 0) {
                position++
                return null
            }

            if (peekOctet(limit) >= TEXT_HEADER_MIN) {
                return decodeText(readTextStringBytes(limit), Charsets.UTF_8, warnings)
            }

            val valueLength = readValueLength(limit)
            val valueEnd = checkedEnd(valueLength, limit)
            val charset = readCharset(valueEnd, warnings)
            val bytes = readTextStringBytes(valueEnd)
            if (position != valueEnd) throw MalformedPdu(MalformedReason.INVALID_LENGTH)
            return decodeText(bytes, charset, warnings)
        }

        fun readFromValue(warnings: MutableSet<Warning>): String? {
            val valueLength = readValueLength()
            val valueEnd = checkedEnd(valueLength)
            val token = readOctet(valueEnd)
            val sender = when (token) {
                FROM_ADDRESS_PRESENT -> readEncodedString(warnings, valueEnd)
                FROM_INSERT_ADDRESS -> null
                else -> throw MalformedPdu(MalformedReason.INVALID_FROM)
            }
            if (position != valueEnd) throw MalformedPdu(MalformedReason.INVALID_LENGTH)
            return sender
        }

        fun readExpiryValue() {
            val valueLength = readValueLength()
            val valueEnd = checkedEnd(valueLength)
            val token = readOctet(valueEnd)
            if (token != VALUE_ABSOLUTE && token != VALUE_RELATIVE) {
                throw MalformedPdu(MalformedReason.INVALID_EXPIRY)
            }
            readLongInteger(valueEnd)
            if (position != valueEnd) throw MalformedPdu(MalformedReason.INVALID_LENGTH)
        }

        fun skipFieldValue(limit: Int = data.size) {
            val first = peekOctet(limit)
            when {
                first <= SHORT_LENGTH_MAX || first == LENGTH_QUOTE -> {
                    val length = readValueLength(limit)
                    position = checkedEnd(length, limit)
                }
                first in TEXT_HEADER_MIN..TEXT_HEADER_MAX -> readTextStringBytes(limit)
                else -> position++ // Short-integer or a one-octet token.
            }
        }

        private fun readCharset(limit: Int, warnings: MutableSet<Warning>): Charset? {
            val first = peekOctet(limit)
            if (first >= SHORT_INTEGER_FLAG) {
                return charsetForMibEnum(readShortInteger(limit), warnings)
            }
            if (first <= SHORT_LENGTH_MAX) {
                return charsetForMibEnum(readLongInteger(limit).toInt(), warnings)
            }
            if (first == LENGTH_QUOTE) {
                throw MalformedPdu(MalformedReason.INVALID_INTEGER)
            }

            val charsetName = decodeText(readTextStringBytes(limit), Charsets.US_ASCII, warnings)
                ?: return null
            return runCatching { Charset.forName(charsetName) }
                .getOrElse {
                    warnings += Warning.UNSUPPORTED_CHARSET
                    null
                }
        }

        private fun readTextStringBytes(limit: Int): ByteArray {
            if (position >= limit) throw MalformedPdu(MalformedReason.TRUNCATED)
            if (peekOctet(limit) == QUOTE) position++
            val start = position
            while (position < limit && data[position].toInt() != 0) {
                if (position - start >= MAX_METADATA_BYTES) {
                    throw MalformedPdu(MalformedReason.METADATA_TOO_LARGE)
                }
                position++
            }
            if (position >= limit) throw MalformedPdu(MalformedReason.TRUNCATED)
            val value = data.copyOfRange(start, position)
            position++ // End-of-string octet.
            return value
        }

        private fun readValueLength(limit: Int = data.size): Int {
            return when (val first = readOctet(limit)) {
                in 0..SHORT_LENGTH_MAX -> first
                LENGTH_QUOTE -> readUintvar(limit)
                else -> throw MalformedPdu(MalformedReason.INVALID_LENGTH)
            }
        }

        private fun readUintvar(limit: Int): Int {
            var value = 0L
            repeat(MAX_UINTVAR_OCTETS) {
                val octet = readOctet(limit)
                value = (value shl UINTVAR_BITS_PER_OCTET) or (octet and SHORT_INTEGER_VALUE_MASK).toLong()
                if (value > Int.MAX_VALUE) throw MalformedPdu(MalformedReason.INVALID_LENGTH)
                if (octet and SHORT_INTEGER_FLAG == 0) return value.toInt()
            }
            throw MalformedPdu(MalformedReason.INVALID_LENGTH)
        }

        private fun peekOctet(limit: Int): Int {
            if (position >= limit || position >= data.size) {
                throw MalformedPdu(MalformedReason.TRUNCATED)
            }
            return data[position].toInt() and OCTET_MASK
        }

        private fun checkedEnd(length: Int, limit: Int = data.size): Int {
            if (length < 0 || length > limit - position) {
                throw MalformedPdu(MalformedReason.TRUNCATED)
            }
            return position + length
        }
    }

    private fun decodeText(
        bytes: ByteArray,
        charset: Charset?,
        warnings: MutableSet<Warning>?,
    ): String? {
        if (bytes.isEmpty() || charset == null) return null
        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .trim()
                .ifBlank { null }
        } catch (_: CharacterCodingException) {
            warnings?.add(Warning.INVALID_TEXT_ENCODING)
            null
        }
    }

    private fun charsetForMibEnum(value: Int, warnings: MutableSet<Warning>): Charset? {
        val charsetName = when (value) {
            3 -> "US-ASCII"
            4 -> "ISO-8859-1"
            17 -> "Shift_JIS"
            106 -> "UTF-8"
            1013 -> "UTF-16BE"
            1014 -> "UTF-16LE"
            1015 -> "UTF-16"
            2025 -> "GB2312"
            2026 -> "Big5"
            else -> null
        }
        if (charsetName == null) {
            warnings += Warning.UNSUPPORTED_CHARSET
            return null
        }
        return runCatching { Charset.forName(charsetName) }
            .getOrElse {
                warnings += Warning.UNSUPPORTED_CHARSET
                null
            }
    }

    private fun String.withoutAddressType(): String {
        val typeSuffix = indexOf("/TYPE=", ignoreCase = true)
        return if (typeSuffix > 0) substring(0, typeSuffix) else this
    }

    private class MalformedPdu(val reason: MalformedReason) : RuntimeException()
}
