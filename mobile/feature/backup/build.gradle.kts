plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.mobilefeature.backup"

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
    implementation(project(":core"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":magisk-ui-kit"))
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.core)
}
