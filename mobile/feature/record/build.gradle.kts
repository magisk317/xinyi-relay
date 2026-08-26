plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
    alias(libs.plugins.kotlin.serialization)
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.mobilefeature.record"

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable.add("MissingTranslation")
        disable.add("LocalContextGetResourceValueCall")
    }
}

dependencies {
    implementation(project(":relay:android"))
    implementation(project(":relay:contract"))
    implementation(project(":relay:engine:api"))
    implementation(project(":core"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":smscode-core:runtime"))
    implementation(project(":smscode-core:rule"))
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
