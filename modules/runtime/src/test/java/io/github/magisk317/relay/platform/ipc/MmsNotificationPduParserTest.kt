package io.github.magisk317.relay.platform.ipc

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class MmsNotificationPduParserTest {

    @Test
    fun parse_decodesStandardsCompliantNotificationMetadata() {
        val result = MmsNotificationPduParser.parse(mmsNotificationPduFixture())

        assertTrue(result is MmsNotificationPduParser.Result.Parsed)
        result as MmsNotificationPduParser.Result.Parsed
        assertTrue(result.isComplete)
        assertTrue(result.warnings.isEmpty())
        assertEquals("15551234", result.metadata.sender)
        assertEquals("你好", result.metadata.subject)
        assertEquals("http://mmsc.test/m/42", result.metadata.contentLocation)
        assertEquals("tx-123", result.metadata.transactionId)
    }

    @Test
    fun parse_skipsExtensionHeadersWithoutLosingKnownMetadata() {
        val result = MmsNotificationPduParser.parse(
            mmsNotificationPduFixture(includeExtensionHeader = true),
        )

        assertTrue(result is MmsNotificationPduParser.Result.Parsed)
        result as MmsNotificationPduParser.Result.Parsed
        assertEquals("15551234", result.metadata.sender)
        assertEquals("http://mmsc.test/m/42", result.metadata.contentLocation)
    }

    @Test
    fun parse_supportsLengthQuotedEncodedStrings() {
        val longSubject = "carrier-notification-" + "x".repeat(40)

        val result = MmsNotificationPduParser.parse(
            mmsNotificationPduFixture(subject = longSubject),
        )

        assertTrue(result is MmsNotificationPduParser.Result.Parsed)
        result as MmsNotificationPduParser.Result.Parsed
        assertEquals(longSubject, result.metadata.subject)
    }

    @Test
    fun parse_reportsMissingMandatoryHeadersInsteadOfClaimingCompleteness() {
        val result = MmsNotificationPduParser.parse(
            mmsNotificationPduFixture(includeEnvelopeHeaders = false),
        )

        assertTrue(result is MmsNotificationPduParser.Result.Parsed)
        result as MmsNotificationPduParser.Result.Parsed
        assertFalse(result.isComplete)
        assertEquals(
            setOf(
                MmsNotificationPduParser.MandatoryHeader.MESSAGE_CLASS,
                MmsNotificationPduParser.MandatoryHeader.MESSAGE_SIZE,
                MmsNotificationPduParser.MandatoryHeader.EXPIRY,
            ),
            result.missingMandatoryHeaders,
        )
        assertEquals("15551234", result.metadata.sender)
    }

    @Test
    fun parse_reportsTruncatedPduAsMalformed() {
        val result = MmsNotificationPduParser.parse(
            byteArrayOf(0x8C.toByte(), 0x82.toByte(), 0x98.toByte()),
        )

        assertEquals(
            MmsNotificationPduParser.Result.Malformed(
                MmsNotificationPduParser.MalformedReason.TRUNCATED,
            ),
            result,
        )
    }

    @Test
    fun parse_rejectsNonNotificationMessageType() {
        val result = MmsNotificationPduParser.parse(
            byteArrayOf(0x8C.toByte(), 0x80.toByte(), 0x8D.toByte(), 0x90.toByte()),
        )

        assertEquals(MmsNotificationPduParser.Result.Unsupported(0x80), result)
    }

    @Test
    fun parse_neverThrowsForArbitraryInput() {
        val random = Random(317)

        repeat(1_000) {
            val bytes = random.nextBytes(random.nextInt(from = 0, until = 257))
            assertDoesNotThrow { MmsNotificationPduParser.parse(bytes) }
        }
    }
}

internal fun mmsNotificationPduFixture(
    includeEnvelopeHeaders: Boolean = true,
    includeExtensionHeader: Boolean = false,
    subject: String = "你好",
): ByteArray = buildList {
    addOctets(0x8C, 0x82) // X-Mms-Message-Type: m-notification-ind
    addTextHeader(0x98, "tx-123")
    addOctets(0x8D, 0x90) // X-Mms-MMS-Version: 1.0

    if (includeExtensionHeader) {
        addText("X-Carrier-Metadata")
        addText("ignored")
    }

    val from = "15551234/TYPE=PLMN".encodeToByteArray() + byteArrayOf(0)
    add(0x89.toByte())
    addValueLength(1 + from.size)
    add(0x80.toByte()) // Address-present-token
    addAll(from.toList())

    if (includeEnvelopeHeaders) {
        addOctets(0x8A, 0x80) // X-Mms-Message-Class: Personal
        addOctets(0x8E, 0x02, 0x04, 0xD2) // X-Mms-Message-Size: 1234
        addOctets(0x88, 0x04, 0x81, 0x02, 0x0E, 0x10) // Expiry: relative 3600s
    }

    val encodedSubject = subject.encodeToByteArray() + byteArrayOf(0)
    add(0x96.toByte())
    addValueLength(1 + encodedSubject.size)
    add(0xEA.toByte()) // IANA MIBenum 106: UTF-8
    addAll(encodedSubject.toList())

    addTextHeader(0x83, "http://mmsc.test/m/42")
}.toByteArray()

private fun MutableList<Byte>.addOctets(vararg values: Int) {
    values.forEach { add(it.toByte()) }
}

private fun MutableList<Byte>.addTextHeader(header: Int, value: String) {
    add(header.toByte())
    addText(value)
}

private fun MutableList<Byte>.addText(value: String) {
    addAll(value.encodeToByteArray().toList())
    add(0)
}

private fun MutableList<Byte>.addValueLength(length: Int) {
    if (length <= 30) {
        add(length.toByte())
    } else {
        add(0x1F)
        add(length.toByte())
    }
}
