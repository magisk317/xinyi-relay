plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
    id("magisk.android.distribution")
}

android {

    buildFeatures {
        compose = true
        buildConfig = true
    }

    namespace = "io.github.magisk317.relay.mobilefeature.appconfig"
}

dependencies {
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":relay:android"))
    implementation(project(":relay:engine:api"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":mobile:core"))
    implementation(project(":runtime"))
    implementation(project(":smscode-core:contract"))
    implementation(project(":smscode-core:db"))
    implementation(project(":relay:sender:api"))
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.collections.immutable)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
