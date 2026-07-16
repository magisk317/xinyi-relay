plugins {
    id("magisk.android.library")
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
    implementation(project(":magisk-xposed-kit"))
    api(project(":relay:contract"))
    implementation(project(":smscode-core:runtime"))
    implementation(project(":smscode-core:verification"))
    implementation(project(":smscode-core:hook"))

    implementation(libs.androidx.core.ktx)
}
