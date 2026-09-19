plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
    id("relay.android.common")
}


android {
    namespace = "io.github.magisk317.relay.mobilefeature.settings"

    defaultConfig {
        buildConfigField("int", "VERSION_CODE", libs.versions.versionCode.get())
        buildConfigField("String", "VERSION_NAME", "\"${libs.versions.versionName.get()}\"")
    }

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
    implementation(project(":relay:android"))
    implementation(project(":relay:contract"))
    implementation(project(":relay:engine:api"))
    implementation(project(":policy"))
    implementation(project(":core"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":magisk-xposed-kit:diagnostics"))
    implementation(project(":magisk-xposed-kit:permission"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":smscode-core:runtime"))
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
