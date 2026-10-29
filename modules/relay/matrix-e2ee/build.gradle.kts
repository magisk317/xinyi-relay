plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvm()

    android {
        namespace = "io.github.magisk317.relay.matrix.e2ee"
        compileSdk(project.magiskCompileSdk())
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":magisk-forward-core:sender:api"))
            implementation(project(":magisk-forward-core:net"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.okhttp)
        }
        androidMain.dependencies {
            implementation(project(":magisk-xposed-kit:logging"))
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.matrix.sdk.android)
            implementation(libs.rustls.platform.verifier)
        }
    }
}

dependencies {
    add("jvmTestImplementation", libs.junit.jupiter)
    add("jvmTestImplementation", libs.okhttp.mockwebserver)
    add("jvmTestImplementation", libs.kotlinx.coroutines.core)
    add("jvmTestImplementation", libs.kotest.property)
    add("jvmTestRuntimeOnly", libs.junit.platform.launcher)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
