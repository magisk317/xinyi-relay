plugins {
    id("magisk.android.library")
    id("magisk.android.distribution")
}

android {
    namespace = "io.github.magisk317.relay.security"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
