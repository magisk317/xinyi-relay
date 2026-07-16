plugins {
    id("magisk.android.library")
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.policy"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

dependencies {
    implementation(project(":relay:android"))
    implementation(project(":smscode-core:runtime"))
    implementation(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
}
