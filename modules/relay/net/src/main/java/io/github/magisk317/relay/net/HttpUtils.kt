package io.github.magisk317.relay.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object HttpUtils {

    private val client = RelayHttpClients.default

    val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun postJson(url: String, json: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val body = json.toRequestBody(JSON)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure(Exception("HTTP Failed: ${response.code} ${response.message}"))
                } else {
                    val respBody = response.body.string()
                    Result.success(respBody)
                }
            }
        } catch (e: java.io.IOException) {
            Result.failure(e)
        }
    }

    suspend fun get(url: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure(Exception("HTTP Failed: ${response.code} ${response.message}"))
                } else {
                    val respBody = response.body.string()
                    Result.success(respBody)
                }
            }
        } catch (e: java.io.IOException) {
            Result.failure(e)
        }
    }
}
