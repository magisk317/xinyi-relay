plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.mobilefeature.rule"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

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
    implementation(project(":relay:android"))
    implementation(project(":relay:contract"))
    implementation(project(":relay:engine:api"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":smscode-core:smscode-domain"))
    implementation(project(":smscode-core:smscode-runtime-common"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
