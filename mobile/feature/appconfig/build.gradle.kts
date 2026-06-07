plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
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

    namespace = "io.github.magisk317.relay.mobilefeature.appconfig"
}

dependencies {
    implementation(project(":relay:engine:api"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":relay:android"))
    implementation(project(":core"))
    implementation(project(":smscode-core:contract"))
    implementation(project(":relay:sender:api"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.haze.android)
    implementation(libs.haze.blur.android)
    implementation(libs.kotlinx.collections.immutable)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
