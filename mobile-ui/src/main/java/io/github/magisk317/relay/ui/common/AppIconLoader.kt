package io.github.magisk317.relay.ui.common

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
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
import io.github.magisk317.relay.common.utils.RomProfile
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

    fun resolveDefaultSmsPackage(context: Context): String? {
        val pm = context.packageManager
        Telephony.Sms.getDefaultSmsPackage(context)?.takeIf { it.isNotBlank() }?.let { return it }
        resolveSmsIntentPackage(pm)?.let { return it }
        return SMS_FALLBACK_PACKAGES.firstOrNull { packageName ->
            runCatching { pm.getApplicationInfo(packageName, 0) }.isSuccess
        }
    }

    fun resolveDefaultDialerPackage(context: Context): String? {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(TelecomManager::class.java)
                ?.defaultDialerPackage
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        resolveDialIntentPackage(pm)?.let { return it }
        val strategy = RomDialerPackageResolver.strategyFor(currentRomProfile())
        return strategy.preferredPackages.firstOrNull { packageName ->
            runCatching { pm.getApplicationInfo(packageName, 0) }.isSuccess
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

private fun resolveDialIntentPackage(pm: PackageManager): String? {
    val intent = Intent(Intent.ACTION_DIAL)
    return runCatching {
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

private fun resolveSmsIntentPackage(pm: PackageManager): String? {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:10086"))
    return runCatching {
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

private fun currentRomProfile(): RomProfile {
    return RomProfile(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        brand = Build.BRAND.orEmpty(),
        display = Build.DISPLAY.orEmpty(),
        miuiVersionName = readSystemProperty("ro.miui.ui.version.name"),
        miOsVersionName = readSystemProperty("ro.mi.os.version.name"),
    )
}

private fun readSystemProperty(key: String): String {
    return runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java, String::class.java)
        (method.invoke(null, key, "") as? String).orEmpty().trim()
    }.getOrDefault("")
}

private val SMS_FALLBACK_PACKAGES = listOf(
    "com.android.mms",
    "com.google.android.apps.messaging",
    "com.android.messaging",
)

private const val MAX_ICON_SIZE_PX = 256
