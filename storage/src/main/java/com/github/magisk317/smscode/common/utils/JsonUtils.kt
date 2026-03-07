package com.github.magisk317.smscode.common.utils

import com.github.magisk317.smscode.common.serialization.JsonConfig
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import java.io.Reader

/**
 * Json utils using Kotlin Serialization
 */
object JsonUtils {

    val json = JsonConfig.json

    @JvmStatic
    inline fun <reified T> toJson(obj: T): String = json.encodeToString(obj)

    @JvmStatic
    inline fun <reified T> toJson(obj: T, writer: Appendable, excludeExposeAnnotation: Boolean = true) {
        writer.append(toJson(obj))
    }

    @JvmStatic
    inline fun <reified T> entityFromJson(
        jsonString: String?,
        typeClass: Class<T>? = null,
        excludeExposeAnnotation: Boolean = true,
    ): T? {
        if (jsonString.isNullOrEmpty()) return null
        // typeClass is ignored in KOS reified, kept for compatibility if needed, but nullable
        return json.decodeFromString<T>(jsonString)
    }

    @JvmStatic
    inline fun <reified T> entityFromJson(
        reader: Reader?,
        typeClass: Class<T>? = null,
        excludeExposeAnnotation: Boolean = true,
    ): T? {
        if (reader == null) return null
        val jsonString = reader.readText()
        return json.decodeFromString<T>(jsonString)
    }

    @JvmStatic
    inline fun <reified T> listFromJson(
        jsonString: String?,
        typeClass: Class<T>? = null,
        excludeExposeAnnotation: Boolean = true,
    ): List<T> {
        if (jsonString.isNullOrEmpty()) return emptyList()
        return json.decodeFromString(jsonString)
    }

    @JvmStatic
    inline fun <reified T> listFromJson(
        reader: Reader?,
        typeClass: Class<T>? = null,
        excludeExposeAnnotation: Boolean = true,
    ): List<T> {
        if (reader == null) return emptyList()
        val jsonString = reader.readText()
        return json.decodeFromString(jsonString)
    }

    @JvmStatic
    fun <T : Any> listFromJson(jsonString: String, entityClass: Class<T>): List<T> {
        val entitySerializer = serializer(entityClass)
        val decoded = json.decodeFromString(ListSerializer(entitySerializer), jsonString)
        return decoded.map { entityClass.cast(it)!! }
    }

    @JvmStatic
    fun <T : Any> listFromJson(reader: Reader?, entityClass: Class<T>): List<T> {
        if (reader == null) return emptyList()
        val jsonString = reader.readText()
        return listFromJson(jsonString, entityClass)
    }

    @JvmStatic
    fun <T : Any> listToJson(list: List<T>, entityClass: Class<T>): String {
        val entitySerializer = serializer(entityClass)
        val checked = list.map { entityClass.cast(it) as Any }
        return json.encodeToString(ListSerializer(entitySerializer), checked)
    }
}
