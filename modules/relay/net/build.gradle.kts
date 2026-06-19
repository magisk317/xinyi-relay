plugins {
    id("magisk.android.library")
    id("magisk.android.common")
}

android {
    namespace = "io.github.magisk317.relay.net"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        buildConfig = true
    }

}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
}
