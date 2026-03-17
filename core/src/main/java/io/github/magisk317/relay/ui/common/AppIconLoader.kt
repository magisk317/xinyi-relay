package io.github.magisk317.relay.ui.common

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import io.github.magisk317.relay.common.utils.RomDialerPackageResolver
import java.util.concurrent.ConcurrentHashMap

data class AppIconRequest(
    val packageName: String?,
    val label: String?,
    val sizePx: Int,
)

object AppIconLoader {

    private val labelToPackageCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var imageLoader: ImageLoader? = null

    fun imageLoader(context: Context): ImageLoader {
        val existing = imageLoader
        if (existing != null) return existing
        return synchronized(this) {
            val cached = imageLoader
            if (cached != null) {
                cached
            } else {
                ImageLoader.Builder(context.applicationContext)
                    .components {
                        add(AppIconKeyer())
                        add(AppIconFetcher.Factory(context.applicationContext))
                    }
                    .build()
                    .also { imageLoader = it }
            }
        }
    }

    internal fun resolvePackageName(pm: PackageManager, packageName: String?, label: String?): String? {
        if (!packageName.isNullOrBlank()) return packageName
        if (label.isNullOrBlank()) return null

        labelToPackageCache[label]?.let { return it }

        RomDialerPackageResolver.resolvePackageName(pm, label)?.let {
            labelToPackageCache[label] = it
            return it
        }

        val installedApps = pm.getInstalledApplications(PackageManager.MATCH_ALL)
        var matchedPackage: String? = null

        // Precise match first.
        for (app in installedApps) {
            if (pm.getApplicationLabel(app).toString() == label) {
                matchedPackage = app.packageName
                break
            }
        }

        // Fuzzy fallback.
        if (matchedPackage == null) {
            for (app in installedApps) {
                val appLabel = pm.getApplicationLabel(app).toString()
                if (label.contains(appLabel) || appLabel.contains(label)) {
                    matchedPackage = app.packageName
                    break
                }
            }
        }

        if (matchedPackage != null) {
            labelToPackageCache[label] = matchedPackage
        }

        return matchedPackage
    }
}

private class AppIconKeyer : Keyer<AppIconRequest> {
    override fun key(data: AppIconRequest, options: Options): String {
        val packagePart = data.packageName.orEmpty()
        val labelPart = data.label.orEmpty()
        return "app_icon:$packagePart:$labelPart:${data.sizePx}"
    }
}

private class AppIconFetcher(
    private val context: Context,
    private val data: AppIconRequest,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val pm = context.packageManager
        val resolvedPackage = AppIconLoader.resolvePackageName(pm, data.packageName, data.label)
            ?: throw IllegalStateException("No package resolved for icon request")

        val appInfo = pm.getApplicationInfo(resolvedPackage, 0)
        val drawable = appInfo.loadIcon(pm)
        val targetSize = data.sizePx.coerceIn(1, MAX_ICON_SIZE_PX)
        val bitmap = drawable.toBitmap(
            width = targetSize,
            height = targetSize,
            config = Bitmap.Config.ARGB_8888,
        )

        return ImageFetchResult(
            image = bitmap.asImage(shareable = false),
            isSampled = false,
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppIconRequest> {
        override fun create(
            data: AppIconRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = AppIconFetcher(context = context, data = data)
    }
}

private const val MAX_ICON_SIZE_PX = 256
