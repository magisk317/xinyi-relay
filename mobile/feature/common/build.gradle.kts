plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.mobilefeature.common"

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
    implementation(project(":relay:engine:api"))
    implementation(project(":relay:contract"))
    implementation(project(":magisk-ui-kit"))
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
