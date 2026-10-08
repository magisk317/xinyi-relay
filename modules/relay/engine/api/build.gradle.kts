plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
}

kotlin {
    jvm()

    android {
        namespace = "io.github.magisk317.relay.engine.api"
        compileSdk(project.magiskCompileSdk())
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":relay:contract"))
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            api(project(":smscode-core:contract"))
            implementation(libs.androidx.core.ktx)
        }
    }
}
