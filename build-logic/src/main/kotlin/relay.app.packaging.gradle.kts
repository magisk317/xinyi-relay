import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val versionNameStr = libsCatalog.findVersion("versionName").get().requiredVersion

fun releaseTime(): String = SimpleDateFormat("yyMMdd").apply {
    timeZone = TimeZone.getDefault()
}.format(Date())

fun buildTimestampOverride(): String? = project.findProperty("buildTs")
    ?.toString()
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

fun buildTimestamp(): String = buildTimestampOverride() ?: SimpleDateFormat("yyyyMMdd_HHmmss").apply {
    timeZone = TimeZone.getDefault()
}.format(Date())

fun releaseBaseName(versionName: String): String {
    val normalizedVersionName = versionName.replace("\\s+".toRegex(), "_")
    val alreadyHasBuildTimestamp = versionName.matches(Regex(".*-\\d{8}(?:_\\d{6}|\\d{6})$"))
    if (alreadyHasBuildTimestamp) {
        return "XinyiRelay_v$normalizedVersionName"
    }
    val suffix = buildTimestampOverride() ?: releaseTime()
    return "XinyiRelay_v${normalizedVersionName}_$suffix"
}

fun releaseApkName(versionName: String, buildType: String, abiSuffix: String, xposedApiFlavor: String): String {
    return "${abiSuffix}_${xposedApiFlavor}_${releaseBaseName(versionName)}_${buildType}.apk"
}

fun releaseAabName(versionName: String): String = "${releaseBaseName(versionName)}_release.aab"

val debugBuildTimestamp = buildTimestamp()
val isBundleTask = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension> {
        splits {
            abi {
                isEnable = project.hasProperty("buildSplits") && !isBundleTask
                reset()
                include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                isUniversalApk = true
            }
        }
    }

    tasks.matching {
        it.name.startsWith("assemble") && it.name.contains("Play")
    }.configureEach {
        enabled = false
    }

    tasks.matching {
        it.name.startsWith("bundle")
    }.configureEach {
        if (!name.contains("Play") && !name.contains("ClassesTo")) {
            enabled = false
        }
    }

    extensions.getByType<ApplicationAndroidComponentsExtension>().apply {
        beforeVariants(selector().all()) { variantBuilder ->
            val flavors = variantBuilder.productFlavors.toMap()
            val distribution = flavors["distribution"]
            val xposedApiFlavor = flavors["xposedApi"]
            val distributionEnabled = when (distribution) {
                "play" -> xposedApiFlavor == "api101"
                "github" -> xposedApiFlavor == "legacy" || xposedApiFlavor == "api101"
                "fdroid" -> false
                else -> false
            }
            if (!distributionEnabled) {
                variantBuilder.enable = false
                return@beforeVariants
            }

            if (isBundleTask && distribution != "play") {
                variantBuilder.enable = false
            }
        }

        onVariants(selector().all()) { variant ->
            val isDebug = variant.buildType == "debug"
            val suffix = if (isDebug) debugBuildTimestamp else ""
            val resolvedVersionName = if (isDebug) "$versionNameStr-$suffix" else versionNameStr
            val flavorMap = variant.productFlavors.toMap()
            val xposedApiFlavor = flavorMap["xposedApi"] ?: "api101"

            variant.outputs.forEach { output ->
                if (isDebug) {
                    output.versionName.set(resolvedVersionName)
                }

                val abi = output.filters.find {
                    it.filterType == FilterConfiguration.FilterType.ABI
                }?.identifier ?: "universal"

                try {
                    val outputFileName = output.javaClass.getMethod("getOutputFileName").invoke(output)
                    outputFileName.javaClass
                        .getMethod("set", Any::class.java)
                        .invoke(outputFileName, releaseApkName(resolvedVersionName, variant.buildType ?: "", abi, xposedApiFlavor))
                } catch (_: Exception) {
                    // AGP preview APIs can shift; keep the build tolerant here.
                }
            }
        }
    }

    tasks.register("renamePlayReleaseAab") {
        dependsOn("bundlePlayApi101Release")
        val bundleFileProvider = layout.buildDirectory.file("outputs/bundle/playApi101Release/app-play-api101-release.aab")
        val targetFileProvider = layout.buildDirectory.file("outputs/bundle/playApi101Release/${releaseAabName(versionNameStr)}")
        doLast {
            val bundleFile = bundleFileProvider.get().asFile
            if (bundleFile.exists()) {
                val target = targetFileProvider.get().asFile
                bundleFile.copyTo(target, overwrite = true)
            }
        }
    }

    tasks.matching { it.name == "bundlePlayApi101Release" }.configureEach {
        finalizedBy("renamePlayReleaseAab")
    }
}
