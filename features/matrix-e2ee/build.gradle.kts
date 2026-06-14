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
}

dependencies {
    implementation(project(":relay:sender:api"))
    implementation(project(":relay:net"))
    // matrix-rust-sdk FFI for E2EE support
    // Published as "sdk-android" on Maven Central by element-hq
    implementation(libs.matrix.sdk.android)
}

// AGP 9.x dynamic-feature has a bug where extractDeepLinks fails when
// the base app has multiple distribution flavors (play/github/fdroid)
// because it cannot resolve applicationId from the merged flavor.
// The task's applicationId property is evaluated during execution and
// fails before our disable callbacks can take effect.
// TODO: Revisit when AGP fixes the multi-flavor DFM applicationId bug.
