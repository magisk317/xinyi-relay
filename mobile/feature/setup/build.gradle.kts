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

    namespace = "io.github.magisk317.relay.mobilefeature.setup"
}

dependencies {
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":relay:contract"))
    implementation(project(":core"))
    implementation(project(":runtime"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":mobile:feature:common"))
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
