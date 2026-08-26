plugins {
    id("magisk.android.library")
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.matrix.e2ee"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":relay:sender:api"))
    implementation(project(":relay:net"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.matrix.sdk.android)
    implementation("rustls:rustls-platform-verifier:0.1.1")
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.runner.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}
