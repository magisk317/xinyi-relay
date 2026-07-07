package io.github.magisk317.relay.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

object AppIconCache {
    private const val MAX_ICON_SIZE_PX = 256
    private const val MAX_CACHE_KB = 32 * 1024
    private const val MAX_CONCURRENCY = 4

    private val cacheMutex = Mutex()
    private val loadSemaphore = Semaphore(MAX_CONCURRENCY)
    private val bitmapCache = object : LruCache<String, Bitmap>(MAX_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    suspend fun load(
        context: Context,
        packageName: String,
        sizePx: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val normalizedPackage = packageName.trim()
        if (normalizedPackage.isEmpty()) return@withContext null
        val pm = context.packageManager
        val appInfo = runCatching { pm.getApplicationInfo(normalizedPackage, 0) }.getOrNull() ?: return@withContext null
        val targetSize = sizePx.coerceIn(1, MAX_ICON_SIZE_PX)
        val cacheKey = buildCacheKey(appInfo.packageName, appInfo.uid, appInfo.sourceDir.orEmpty(), targetSize)

        cacheMutex.withLock {
            bitmapCache.get(cacheKey)
        }?.let { return@withContext it }

        loadSemaphore.withPermit {
            cacheMutex.withLock {
                bitmapCache.get(cacheKey)
            }?.let { return@withContext it }

            val drawable = runCatching { appInfo.loadIcon(pm) }.getOrNull() ?: return@withContext null
            val bitmap = drawable.toBitmap(
                width = targetSize,
                height = targetSize,
                config = Bitmap.Config.ARGB_8888,
            )
            cacheMutex.withLock {
                bitmapCache.put(cacheKey, bitmap)
            }
            bitmap
        }
    }

    private fun buildCacheKey(packageName: String, uid: Int, sourceDir: String, sizePx: Int): String {
        return "$packageName:$uid:$sourceDir:$sizePx"
    }
}
