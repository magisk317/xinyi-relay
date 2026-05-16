plugins {
    alias(libs.plugins.android.library)
    id(libs.plugins.kotlin.serialization.get().pluginId)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.contract"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        buildConfig = true
    }

    val javaVersion = JavaVersion.toVersion(libs.versions.javaBytecode.get())
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion.toString()))
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
