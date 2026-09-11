plugins {
    id("com.android.test")
}

android {
    namespace = "io.github.magisk317.relay.benchmark"
    compileSdk(project.magiskCompileSdk())

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    flavorDimensions += "distribution"
    productFlavors {
        create("githubNoE2ee") {
            dimension = "distribution"
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}
