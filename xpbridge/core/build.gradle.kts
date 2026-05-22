plugins {
    alias(libs.plugins.android.library)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    id("relay.android.common")
}

val minSdkInt = libs.versions.minSdk.get().toInt()

android {
    namespace = "io.github.magisk317.relay.xpbridgecore"

    defaultConfig {
        minSdk = minSdkInt
    }

    buildFeatures {
        buildConfig = true
    }

}

dependencies {
    implementation(project(":relay:contract"))
    implementation(project(":relay:engine:api"))
    implementation(project(":relay:android"))
    implementation(project(":runtime"))
    implementation(project(":smscode-core:smscode-runtime-common"))
    implementation(project(":smscode-core:smscode-verification-core"))
    implementation(project(":smscode-core:smscode-xposed-core"))

    implementation(libs.androidx.core.ktx)
}
