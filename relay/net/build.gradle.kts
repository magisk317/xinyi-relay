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

}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
}
