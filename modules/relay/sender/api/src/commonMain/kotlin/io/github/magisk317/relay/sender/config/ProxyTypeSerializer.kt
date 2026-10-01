package io.github.magisk317.relay.sender.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.Proxy
import java.util.Locale

object ProxyTypeSerializer : KSerializer<Proxy.Type> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ProxyType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Proxy.Type) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): Proxy.Type {
        val value = decoder.decodeString().trim().uppercase(Locale.ROOT)
        return runCatching { Proxy.Type.valueOf(value) }.getOrDefault(Proxy.Type.DIRECT)
    }
}
