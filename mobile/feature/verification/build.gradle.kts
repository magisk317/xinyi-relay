plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
    id("relay.android.common")
}

android {

    buildFeatures {
        compose = true
        buildConfig = true
    }

    namespace = "io.github.magisk317.relay.mobilefeature.verification"
}

dependencies {
    implementation(project(":relay:android"))
    implementation(project(":relay:contract"))
    implementation(project(":policy"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":core"))
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:rule"))
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}
