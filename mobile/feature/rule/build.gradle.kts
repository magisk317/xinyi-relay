plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.mobilefeature.rule"

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
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:rule"))
    implementation(project(":smscode-core:runtime"))
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
}
