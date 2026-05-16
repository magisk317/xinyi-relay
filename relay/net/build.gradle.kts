plugins {
    alias(libs.plugins.android.library)
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.net"

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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
}
