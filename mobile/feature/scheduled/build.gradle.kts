plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
    id("magisk.android.distribution")
}

android {
    namespace = "io.github.magisk317.relay.mobilefeature.scheduled"

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
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":relay:android"))
    implementation(project(":relay:contract"))
    implementation(project(":magisk-forward-core:engine:api"))
    implementation(project(":mobile:core"))
    implementation(project(":magisk-forward-core:sender:api"))
    implementation(project(":runtime"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":magisk-ui-kit"))
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
}
