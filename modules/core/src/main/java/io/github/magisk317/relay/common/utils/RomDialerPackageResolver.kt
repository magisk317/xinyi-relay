package io.github.magisk317.relay.common.utils

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

enum class RomFamily {
    HYPER_OS,
    AOSP_LIKE,
    UNKNOWN,
}

data class RomProfile(
    val manufacturer: String,
    val brand: String,
    val display: String,
    val miuiVersionName: String,
    val miOsVersionName: String,
)

data class DialerPackageStrategy(
    val family: RomFamily,
    val preferredPackages: List<String>,
)

object RomDialerPackageResolver {

    fun resolvePackageName(pm: PackageManager, label: String): String? {
        if (!isCallLikeLabel(label)) return null

        val profile = currentRomProfile()
        val strategy = strategyFor(profile)

        resolveDialIntentPackage(pm)?.let { resolved ->
            if (resolved in strategy.preferredPackages) return resolved
        }

        strategy.preferredPackages.firstOrNull { packageName ->
            runCatching { pm.getApplicationInfo(packageName, 0) }.isSuccess
        }?.let { return it }

        val installedApps = pm.getInstalledApplications(PackageManager.MATCH_ALL)
        return installedApps.firstOrNull { app ->
            val appLabel = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
            val packageName = app.packageName.lowercase(Locale.ROOT)
            callKeywords().any { keyword -> appLabel.contains(keyword) || packageName.contains(keyword) }
        }?.packageName
    }

    fun strategyFor(profile: RomProfile): DialerPackageStrategy {
        return when (classify(profile)) {
            RomFamily.HYPER_OS -> DialerPackageStrategy(
                family = RomFamily.HYPER_OS,
                preferredPackages = listOf(
                    "com.android.contacts",
                    "com.android.incallui",
                    "com.android.phone",
                    "com.miui.yellowpage",
                    "com.xiaomi.phone",
                ),
            )

            RomFamily.AOSP_LIKE -> DialerPackageStrategy(
                family = RomFamily.AOSP_LIKE,
                preferredPackages = listOf(
                    "com.google.android.dialer",
                    "com.android.dialer",
                    "com.android.incallui",
                    "com.android.phone",
                    "com.android.contacts",
                ),
            )

            RomFamily.UNKNOWN -> DialerPackageStrategy(
                family = RomFamily.UNKNOWN,
                preferredPackages = listOf(
                    "com.google.android.dialer",
                    "com.android.dialer",
                    "com.android.contacts",
                    "com.android.incallui",
                    "com.android.phone",
                    "com.miui.yellowpage",
                    "com.xiaomi.phone",
                ),
            )
        }
    }

    fun classify(profile: RomProfile): RomFamily {
        val haystack = listOf(
            profile.manufacturer,
            profile.brand,
            profile.display,
            profile.miuiVersionName,
            profile.miOsVersionName,
        ).joinToString(" ").lowercase(Locale.ROOT)

        if (profile.miOsVersionName.isNotBlank() || profile.miuiVersionName.isNotBlank() || haystack.contains("hyperos")) {
            return RomFamily.HYPER_OS
        }

        if (
            haystack.contains("aosp") ||
            haystack.contains("pixel") ||
            haystack.contains("lineage") ||
            haystack.contains("evolution") ||
            haystack.contains("infinity") ||
            haystack.contains("crdroid") ||
            haystack.contains("derp") ||
            haystack.contains("arrow") ||
            haystack.contains("nameless") ||
            haystack.contains("google")
        ) {
            return RomFamily.AOSP_LIKE
        }

        return RomFamily.UNKNOWN
    }

    fun isCallLikeLabel(label: String): Boolean {
        val normalized = label.lowercase(Locale.ROOT)
        return normalized.contains("电话") ||
            normalized.contains("來電") ||
            normalized.contains("来电") ||
            normalized.contains("去电") ||
            normalized.contains("未接") ||
            normalized.contains("通话") ||
            normalized.contains("phone") ||
            normalized.contains("dial") ||
            normalized.contains("call")
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

    private fun resolveDialIntentPackage(pm: PackageManager): String? {
        val intent = Intent(Intent.ACTION_DIAL)
        return runCatching {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun readSystemProperty(key: String): String {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            (method.invoke(null, key, "") as? String).orEmpty().trim()
        }.getOrDefault("")
    }

    private fun callKeywords(): List<String> = listOf(
        "dialer",
        "incall",
        "telecom",
        "phone",
        "contact",
        "电话",
        "call",
        "dial",
    )
}
