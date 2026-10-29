plugins {
    id("magisk.android.library")
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    id("magisk.android.distribution")
}

android {
    namespace = "io.github.magisk317.relay.xpbridgecore"

    buildFeatures {
        buildConfig = true
    }

}

dependencies {
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":magisk-xposed-kit"))
    implementation(project(":magisk-xposed-kit:capability"))
    api(project(":xpbridge:android:api"))
    api(project(":relay:contract"))
    implementation(project(":smscode-core:runtime"))
    implementation(project(":smscode-core:verification"))
    implementation(project(":smscode-core:hook"))

    implementation(libs.androidx.core.ktx)
}
