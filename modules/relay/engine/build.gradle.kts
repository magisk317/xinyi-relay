plugins {
    id("magisk.android.library")
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.engine"

    buildFeatures {
        buildConfig = true
    }

}

dependencies {
    api(project(":relay:engine:api"))
    implementation(project(":relay:contract"))
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:verification"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
