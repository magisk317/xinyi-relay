plugins {
    id("magisk.android.library")
    id("magisk.android.distribution")
}

android {
    namespace = "io.github.magisk317.relay.xpbridge.android.api"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(project(":relay:contract"))
}
