package io.github.magisk317.relay.contract.json

import com.google.gson.Gson
import com.google.gson.JsonElement as GsonJsonElement
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.Reader

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

object LegacyGsonJson {
    val gson: Gson = Gson()

    fun toJson(value: Any?): String {
        return gson.toJson(value)
    }

    fun toJsonTree(value: Any?): GsonJsonElement {
        return gson.toJsonTree(value)
    }

    fun <T> fromJson(raw: String, clazz: Class<T>): T {
        return gson.fromJson(raw, clazz)
    }

    fun <T> fromJson(reader: Reader, clazz: Class<T>): T {
        return gson.fromJson(reader, clazz)
    }

    fun <T> fromJson(element: GsonJsonElement, clazz: Class<T>): T {
        return gson.fromJson(element, clazz)
    }
}
