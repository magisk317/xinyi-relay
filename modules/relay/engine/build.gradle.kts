plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvm()

    android {
        namespace = "io.github.magisk317.relay.engine"
        compileSdk(project.magiskCompileSdk())
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":relay:engine:api"))
            implementation(project(":relay:contract"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

dependencies {
    add("jvmTestImplementation", libs.junit.jupiter)
    add("jvmTestRuntimeOnly", libs.junit.platform.launcher)
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}
