plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("relay.android.common")
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
    implementation(project(":relay:engine:api"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":mobile:feature:settings"))
    implementation(project(":relay:android"))
    implementation(project(":core"))
    implementation(project(":smscode-core:smscode-domain"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.activity.compose)
}
