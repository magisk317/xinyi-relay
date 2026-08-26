plugins {
    id("magisk.android.library")
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.sender.api"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":magisk-xposed-kit:logging"))
    api(project(":relay:contract"))
    api(project(":relay:engine:api"))
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
