plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
}

kotlin {
    jvm()

    android {
        namespace = "io.github.magisk317.relay.sender.api"
        compileSdk(project.magiskCompileSdk())
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":relay:contract"))
            api(project(":relay:engine:api"))
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(project(":magisk-xposed-kit:logging"))
            implementation(libs.androidx.core.ktx)
        }
    }
}

dependencies {
    add("jvmTestImplementation", libs.junit.jupiter)
    add("jvmTestRuntimeOnly", libs.junit.platform.launcher)
}
