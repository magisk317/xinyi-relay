package com.github.magisk317.smscode.forwarder.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.google.gson.JsonParser
import com.github.magisk317.smscode.common.utils.XLog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object SourceMetadataResolver {
    private val areaCache = ConcurrentHashMap<String, String>()
    private val areaClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build()

    fun resolveContactName(context: Context, rawSender: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            XLog.d("Contact lookup skipped: READ_CONTACTS not granted")
            return ""
        }
        val normalized = normalizePhoneNumber(rawSender)
        if (normalized.isBlank()) return ""
        return runCatching {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(normalized),
            )
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0).orEmpty().trim()
                } else {
                    ""
                }
            } ?: ""
        }.onFailure {
            XLog.w("Contact lookup failed: %s", it.message ?: "unknown")
        }.getOrDefault("")
    }

    @Suppress("MagicNumber")
    fun resolvePhoneArea(rawSender: String): String {
        val normalized = normalizePhoneNumber(rawSender)
        if (normalized.length < 7) return ""
        areaCache[normalized]?.let { return it }

        val url = "https://cx.shouji.360.cn/phonearea.php?number=$normalized"
        val request = Request.Builder().url(url).get().build()
        val area = runCatching {
            areaClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ""
                val body = response.body.string()
                parsePhoneArea(body)
            }
        }.onFailure {
            XLog.d("Phone area lookup failed for %s: %s", normalized, it.message ?: "unknown")
        }.getOrDefault("")

        if (area.isNotBlank()) {
            areaCache[normalized] = area
        }
        return area
    }

    private fun parsePhoneArea(rawBody: String): String {
        if (rawBody.isBlank()) return ""
        val start = rawBody.indexOf('{')
        val end = rawBody.lastIndexOf('}')
        if (start < 0 || end <= start) return ""
        val jsonBody = rawBody.substring(start, end + 1)
        return runCatching {
            val root = JsonParser.parseString(jsonBody).asJsonObject
            val data = root.getAsJsonObject("data") ?: return@runCatching ""
            val province = data.get("province")?.asString.orEmpty().trim()
            val city = data.get("city")?.asString.orEmpty().trim()
            val carrier = data.get("sp")?.asString.orEmpty().trim()
            listOf(province, city, carrier)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .replace("\\s+".toRegex(), " ")
                .trim()
        }.getOrDefault("")
    }

    @Suppress("MagicNumber")
    private fun normalizePhoneNumber(rawSender: String): String {
        val digitsOnly = rawSender.filter { it.isDigit() }
        if (digitsOnly.isBlank()) return ""
        return if (digitsOnly.startsWith("86") && digitsOnly.length > 11) {
            digitsOnly.removePrefix("86")
        } else {
            digitsOnly
        }
    }
}
