package io.github.magisk317.relay.engine.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

object DateAsMillisSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DateAsMillis", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Date) {
        encoder.encodeLong(value.time)
    }

    override fun deserialize(decoder: Decoder): Date {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            val primitive = runCatching { element.jsonPrimitive }.getOrNull() ?: return Date(0L)
            return parseDatePrimitive(primitive)
        }
        return Date(decoder.decodeLong())
    }

    private fun parseDatePrimitive(primitive: JsonPrimitive): Date {
        primitive.longOrNull?.let { return Date(it) }
        val text = primitive.contentOrNull?.trim().orEmpty()
        if (text.isBlank()) return Date(0L)
        text.toLongOrNull()?.let { return Date(it) }
        runCatching { Date.from(Instant.parse(text)) }.getOrNull()?.let { return it }

        val formats = listOf(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.US),
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US),
        )
        formats.forEach { format ->
            runCatching { format.parse(text) }.getOrNull()?.let { return it }
        }
        return Date(0L)
    }
}
