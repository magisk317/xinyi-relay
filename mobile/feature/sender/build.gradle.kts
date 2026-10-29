plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
    alias(libs.plugins.kotlin.serialization)
    id("magisk.android.distribution")
}

android {
    namespace = "io.github.magisk317.relay.mobilefeature.sender"

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
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":relay:android"))
    implementation(project(":relay:contract"))
    implementation(project(":magisk-forward-core:engine:api"))
    implementation(project(":mobile:core"))
    implementation(project(":magisk-forward-core:sender:api"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":smscode-core:runtime"))
    implementation(project(":smscode-core:db"))
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
