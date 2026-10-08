package io.github.magisk317.relay.sender.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import io.github.magisk317.relay.contract.model.ProxyType

object ProxyTypeSerializer : KSerializer<ProxyType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ProxyType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ProxyType) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): ProxyType {
        val value = decoder.decodeString().trim().uppercase()
        return runCatching { ProxyType.valueOf(value) }.getOrDefault(ProxyType.DIRECT)
    }
}
