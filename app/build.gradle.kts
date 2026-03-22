import java.io.FileInputStream
import java.util.Properties
import java.util.TimeZone
import java.util.Date
import java.text.SimpleDateFormat
import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.application)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    alias(libs.plugins.ksp)
    id(libs.plugins.kotlin.compose.get().pluginId)
    id(libs.plugins.kotlin.serialization.get().pluginId)
    alias(libs.plugins.google.services)
}

apply(from = rootProject.file("gradle/relay-android-common.gradle"))

val keystoreFilePath = System.getenv("KEYSTORE_FILE") ?: findProperty("tianma.keystore.path")?.toString() ?: "release.jks"
val keyFile = file(keystoreFilePath)
val propertyFile = file(findProperty("tianma.signature.path") ?: "signature.properties")

val keyProps = Properties()
if (propertyFile.exists()) {
    FileInputStream(propertyFile).use { keyProps.load(it) }
}

val isSigningInfoAvailable = keyFile.exists() &&
    (keyProps.getProperty("STORE_PASSWORD") != null || System.getenv("STORE_PASSWORD") != null)

fun releaseTime(): String {
    return SimpleDateFormat("yyMMdd").apply { timeZone = TimeZone.getDefault() }.format(Date())
}

fun buildTimestampOverride(): String? {
    val override = findProperty("buildTs")?.toString()?.trim().orEmpty()
    return override.ifBlank { null }
}

fun buildTimestamp(): String {
    return buildTimestampOverride()
        ?: SimpleDateFormat("yyyyMMdd_HHmmss").apply { timeZone = TimeZone.getDefault() }.format(Date())
}

val versionNameStr = libs.versions.versionName.get()
val versionCodeInt = libs.versions.versionCode.get().toInt()
val minSdkStr = libs.versions.minSdk.get()
val targetSdkStr = libs.versions.targetSdk.get()
val ndkVersionStr = libs.versions.ndk.get()
val allowConflictBypass = findProperty("allowConflictBypass")
    ?.toString()
    ?.toBooleanStrictOrNull()
    ?: false

fun releaseBaseName(versionName: String): String {
    val normalizedVersionName = versionName.replace("\\s+".toRegex(), "_")
    val alreadyHasBuildTimestamp = Regex(""".*-\d{8}(?:_\d{6}|\d{6})$""").matches(versionName)
    return if (alreadyHasBuildTimestamp) {
        "XinyiRelay_v$normalizedVersionName"
    } else {
        val suffix = buildTimestampOverride() ?: releaseTime()
        "XinyiRelay_v${normalizedVersionName}_$suffix"
    }
}

fun releaseApkName(versionName: String, buildType: String, abiSuffix: String): String {
    return "${abiSuffix}_${releaseBaseName(versionName)}_${buildType}.apk"
}

fun releaseAabName(versionName: String): String {
    return "${releaseBaseName(versionName)}_release.aab"
}

val debugBuildTimestamp = buildTimestamp()
val isBundleTask = gradle.startParameter.taskNames.any { name ->
    val lowered = name.lowercase()
    lowered.contains("bundle")
}

android {
    namespace = "io.github.magisk317.relay"
    ndkVersion = ndkVersionStr

    androidResources {
        localeFilters.addAll(listOf("en", "zh-rCN", "zh-rTW"))
    }

    defaultConfig {
        applicationId = "io.github.magisk317.xinyi.relay"
        val minSdkCodename = minSdkStr.removePrefix("android-")
        val minSdkAsInt = minSdkCodename.toIntOrNull()
        if (minSdkAsInt != null) {
            minSdk = minSdkAsInt
        } else {
            @Suppress("DEPRECATION")
            minSdkPreview = minSdkCodename
        }
        
        val targetSdkCodename = targetSdkStr.removePrefix("android-")
        val targetSdkAsInt = targetSdkCodename.toIntOrNull()
        if (targetSdkAsInt != null) {
            targetSdk = targetSdkAsInt
        } else {
            targetSdkPreview = targetSdkCodename
        }

        versionCode = versionCodeInt
        versionName = versionNameStr

        buildConfigField("String", "LOG_TAG", "\"relay\"")
        buildConfigField("int", "MODULE_VERSION", "$versionCodeInt")
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", allowConflictBypass.toString())
    }

    splits {
        abi {
            // Disable ABI splits when building App Bundle, even if -PbuildSplits is passed.
            isEnable = hasProperty("buildSplits") && !isBundleTask
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        resources {
            excludes += "**/*.kotlin_*"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
        create("release") {
            storeFile = keyFile
            storePassword = System.getenv("STORE_PASSWORD") ?: keyProps.getProperty("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS") ?: keyProps.getProperty("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD") ?: keyProps.getProperty("KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("int", "LOG_LEVEL", "2")
            buildConfigField("boolean", "LOG_TO_XPOSED", "true")
            if (isSigningInfoAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("alpha") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false

            buildConfigField("int", "LOG_LEVEL", "2")
            buildConfigField("boolean", "LOG_TO_XPOSED", "true")

            if (isSigningInfoAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            buildConfigField("int", "LOG_LEVEL", "4")
            buildConfigField("boolean", "LOG_TO_XPOSED", "true")
            if (isSigningInfoAvailable) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                debugSymbolLevel = "FULL"
            }
            lint {
                disable += "MissingTranslation"
                checkReleaseBuilds = false
            }
        }
    }

    val javaVersion = JavaVersion.toVersion(libs.versions.javaBytecode.get())
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion.toString()))
        }
    }
}

val webuiDir = file("${rootProject.projectDir}/webui")

val buildWebUi by tasks.registering(Exec::class) {
    group = "webui"
    description = "Build the WebUI from webui/ into webui/dist."
    workingDir = webuiDir
    commandLine("pnpm", "build")
}

val syncWebUiAssets by tasks.registering(Exec::class) {
    group = "webui"
    description = "Sync webui/dist into app/src/main/assets/webui."
    workingDir = webuiDir
    commandLine("pnpm", "sync-dist")
    dependsOn(buildWebUi)
}

val checkWebUiAssets by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verify embedded WebUI assets match the latest dist output."
    workingDir = webuiDir
    commandLine("pnpm", "check-dist")
    dependsOn(buildWebUi)
}

tasks.register("webuiBuild") {
    group = "webui"
    description = "Compatibility alias for buildWebUi."
    dependsOn(buildWebUi)
}

tasks.register("webuiSync") {
    group = "webui"
    description = "Compatibility alias for syncWebUiAssets."
    dependsOn(syncWebUiAssets)
}

tasks.named("preBuild").configure {
    dependsOn(checkWebUiAssets)
}

// Disable assemble tasks for Play variants.
tasks.matching {
    it.name.startsWith("assemble") && it.name.contains("Play")
}.configureEach {
    enabled = false
}

// Only keep Play bundle tasks; disable bundle tasks for other flavors.
tasks.matching {
    it.name.startsWith("bundle")
}.configureEach {
    // Keep classpath jar tasks enabled; KSP depends on them.
    if (!name.contains("Play") && !name.contains("ClassesTo")) {
        enabled = false
    }
}

androidComponents {
    beforeVariants(selector().all()) { variantBuilder ->
        if (isBundleTask) {
            val isPlayVariant = variantBuilder.productFlavors.any { it.second == "play" }
            if (!isPlayVariant) {
                variantBuilder.enable = false
            }
        }
    }
    onVariants(selector().all()) { variant ->
        val isDebug = variant.buildType == "debug"
        val suffix = if (isDebug) debugBuildTimestamp else ""
        val vName = if (isDebug) "$versionNameStr-$suffix" else versionNameStr
        
        variant.outputs.forEach { output ->
            if (isDebug) {
                output.versionName.set(vName)
            }
            val abi = output.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier ?: "universal"
            // Use reflection or search for the property if outputFileName is unresolved
            try {
                val outputFileName = output.javaClass.getMethod("getOutputFileName").invoke(output)
                outputFileName.javaClass
                    .getMethod("set", Any::class.java)
                    .invoke(outputFileName, releaseApkName(vName, variant.buildType ?: "", abi))
            } catch (e: Exception) {
                // Ignore for now, build will fail if this is wrong
            }
        }

    }
}

tasks.register("renamePlayReleaseAab") {
    dependsOn("bundlePlayRelease")
    val bundleFileProvider = layout.buildDirectory.file("outputs/bundle/playRelease/app-play-release.aab")
    val targetFileProvider = layout.buildDirectory.file("outputs/bundle/playRelease/${releaseAabName(versionNameStr)}")
    doLast {
        val bundleFile = bundleFileProvider.get().asFile
        if (bundleFile.exists()) {
            val target = targetFileProvider.get().asFile
            bundleFile.copyTo(target, overwrite = true)
        }
    }
}

tasks.matching { it.name == "bundlePlayRelease" }.configureEach {
    finalizedBy("renamePlayReleaseAab")
}

// Google Services is only required for play/github flavors. Disable its tasks for fdroid to avoid requiring json.
tasks.matching {
    it.name.startsWith("processFdroid") && it.name.endsWith("GoogleServices")
}.configureEach {
    enabled = false
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))
    compileOnly(project(":smscode-core:smscode-xposed-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.retrofit.converter.scalars)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.netty)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp.tls)

    add("playImplementation", platform(libs.firebase.bom))
    add("playImplementation", libs.firebase.analytics)
    add("githubImplementation", platform(libs.firebase.bom))
    add("githubImplementation", libs.firebase.analytics)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)

    implementation(libs.haze.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)

    implementation(libs.timber)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.collections.immutable)
}
