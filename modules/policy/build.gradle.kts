plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvm()

    android {
        namespace = "io.github.magisk317.relay.policy"
        compileSdk(project.magiskCompileSdk())
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            // api, deliberately: the policy API surface exposes smscode runtime
            // contract types (PrefRead et al) to its consumers by design.
            api(project(":smscode-core:runtime"))
            implementation(libs.androidx.core.ktx)
        }
    }
}

dependencies {
    add("jvmTestImplementation", libs.junit.jupiter)
    add("jvmTestImplementation", libs.kotest.property)
    add("jvmTestImplementation", libs.mockk)
    add("jvmTestRuntimeOnly", libs.junit.platform.launcher)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
