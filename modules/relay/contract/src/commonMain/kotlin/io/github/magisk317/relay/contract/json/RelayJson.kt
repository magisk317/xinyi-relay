package io.github.magisk317.relay.contract.json

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalSerializationApi::class)
object RelayJson {
    val format: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun parseElement(raw: String): JsonElement {
        return format.parseToJsonElement(raw)
    }

    fun <T> encode(serializer: SerializationStrategy<T>, value: T): String {
        return format.encodeToString(serializer, value)
    }

    fun <T> decode(deserializer: DeserializationStrategy<T>, raw: String): T {
        return format.decodeFromString(deserializer, raw)
    }
}
