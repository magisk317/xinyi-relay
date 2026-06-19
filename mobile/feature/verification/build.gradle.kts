plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
    id("magisk.android.common")
}

val minSdkInt = libs.versions.minSdk.get().toInt()

android {
    defaultConfig {
        minSdk = minSdkInt
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    namespace = "io.github.magisk317.relay.mobilefeature.verification"
}

dependencies {
    implementation(project(":relay:contract"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":relay:android"))
    implementation(project(":core"))
    implementation(project(":smscode-core:domain"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.activity.compose)
}
