plugins {
    id("magisk.android.application")
    // AGP 9 has built-in Kotlin support; do NOT apply org.jetbrains.kotlin.android.
    alias(libs.plugins.kotlin.serialization)
    id("magisk.app.signing")
}

import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

// NOTE: deliberately NOT applying relay.android.common -- the plugin is a
// standalone APK outside the app's distribution flavor matrix, and inheriting
// the play/github flavors would rename every task (assemblePlayRelease etc.)
// and split outputs per flavor.

// The plugin is versioned by the upstream matrix-rust-sdk it bundles, NOT by the
// app release cadence: a new plugin only ships when the SDK is upgraded.
val matrixSdkVersion = libs.versions.matrix.sdk.android.get()

// A class-based task keeps every input behind a Property, which the
// configuration cache can serialize (capturing catalog accessors inside an
// anonymous task closure is not serializable).
abstract class GeneratePluginManifestTask @Inject constructor() : org.gradle.api.DefaultTask() {
    @get:Input
    abstract val sdkVersion: Property<String>

    @get:Input
    abstract val baseVersionCode: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

    @TaskAction
    fun generate() {
        val manifest = outputDir.get().file("plugin-manifest.json").asFile
        manifest.parentFile?.mkdirs()
        val sdk = sdkVersion.get()
        val contractVersion = 1
        val baseVersion = baseVersionCode.get()
        manifest.writeText(
            """
            {
              "sdkVersion": "$sdk",
              "contractVersion": $contractVersion,
              "minBaseVersionCode": $baseVersion
            }
            """.trimIndent() + "\n",
        )
    }
}

val pluginManifestDir = layout.buildDirectory.dir("generated/pluginManifest")
val generatePluginManifest = tasks.register("generatePluginManifest", GeneratePluginManifestTask::class) {
    description = "Generate assets/plugin-manifest.json with the resolved SDK version."
    sdkVersion.set(libs.versions.matrix.sdk.android)
    baseVersionCode.set(libs.versions.versionCode.map { it.toInt() })
    outputDir.set(pluginManifestDir)
}

android {
    namespace = "io.github.magisk317.relay.matrix.e2ee.plugin"

    defaultConfig {
        applicationId = "io.github.magisk317.xinyi.relay.matrixe2eeplugin"
        minSdk = libs.versions.minSdk.get().toInt()

        versionCode = 1
        versionName = matrixSdkVersion
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            // Never obfuscate this APK. The host loads it through a DexClassLoader
            // whose parent is the host classloader, so parent-first delegation
            // resolves every class name the plugin references. R8 would rename
            // this APK's classes (the bundled matrix-rust-sdk included) to short
            // names like z0/a1 that collide with the host's own independently
            // obfuscated names — the plugin would then bind to the host's
            // unrelated classes and fail with NoSuchFieldError/ClassCastException.
            // Package-qualified real names cannot collide.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(pluginManifestDir.get().asFile.path)
        }
    }

    // One APK per ABI: the native libmatrix_sdk_ffi.so is ~64-74MB per ABI and a
    // universal APK would quadruple the download for every device.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

// Ensure the manifest generator runs before any asset merging.
tasks.named("preBuild") {
    dependsOn(generatePluginManifest)
}

// Standard artifact name shared with the host loader
// (E2eePluginLoader.pluginFileName): xinyi-e2ee-plugin_v<sdk>_<abi>.apk
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find {
                it.filterType == FilterConfiguration.FilterType.ABI
            }?.identifier ?: "universal"
            output.outputFileName.set("xinyi-e2ee-plugin_v${matrixSdkVersion}_${abi}.apk")
        }
    }
}

dependencies {
    // The plugin dex references the sender API contracts (provider objects) but
    // must NOT package them: the host app already carries those classes and
    // parent-first delegation resolves them at load time.
    compileOnly(project(":relay:sender:api"))

    implementation(project(":relay:matrix-e2ee"))
    implementation(libs.matrix.sdk.android)
    implementation("rustls:rustls-platform-verifier:0.1.1")
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
}
