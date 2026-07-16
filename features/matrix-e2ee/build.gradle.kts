plugins {
    id("com.android.dynamic-feature")
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.feature.matrix.e2ee"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            // jna.aar still ships deprecated Android ABIs that the base app no
            // longer publishes. BundleTool rejects the app bundle if the DFM
            // advertises more ABIs than the base module.
            excludes += setOf(
                "**/armeabi/*.so",
                "**/mips/*.so",
                "**/mips64/*.so",
            )
        }
    }
}

dependencies {
    implementation(project(":app"))
    implementation(project(":relay:sender:api"))
    implementation(project(":relay:sender"))
    implementation(project(":relay:matrix-e2ee"))
}
