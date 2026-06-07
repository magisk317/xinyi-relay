plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    id("relay.android.common")
}

val minSdkInt = libs.versions.minSdk.get().toInt()

android {
    namespace = "io.github.magisk317.relay.mobilefeature.overview"

    defaultConfig {
        minSdk = minSdkInt
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable.add("MissingTranslation")
        disable.add("LocalContextGetResourceValueCall")
        disable.add("NonObservableLocale")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":relay:android"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":relay:engine:api"))
    implementation(project(":relay:sender:api"))
    implementation(project(":relay:contract"))
    implementation(project(":runtime"))
    implementation(project(":smscode-core:smscode-runtime-common"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.haze.android)
    implementation(libs.haze.blur.android)
    implementation(libs.coil3.core)
    implementation(libs.coil3.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.timber)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
