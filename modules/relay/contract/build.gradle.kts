plugins {
    id("magisk.android.library")
    id(libs.plugins.kotlin.serialization.get().pluginId)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.contract"

    buildFeatures {
        buildConfig = true
    }

}

dependencies {
    api(project(":smscode-core:contract"))
    api(project(":smscode-core:runtime"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
