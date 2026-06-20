plugins {
    id("magisk.android.library")
    id("magisk.android.compose")
    id("relay.android.common")
}

val minSdkInt = libs.versions.minSdk.get().toInt()

android {
    namespace = "io.github.magisk317.relay.mobilefeature.relayconfig"

    defaultConfig {
        minSdk = minSdkInt
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
    implementation(project(":core"))
    implementation(project(":relay:engine:api"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":mobile:feature:common"))
    implementation(project(":relay:contract"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.haze.android)
    implementation(libs.haze.blur.android)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.play.services.code.scanner)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
