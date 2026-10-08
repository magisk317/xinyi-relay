import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    android {
        namespace = "io.github.magisk317.relay.contract"
        compileSdk(project.magiskCompileSdk())
        minSdk = libs.versions.minSdk.get().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            api(project(":smscode-core:contract"))
            api(project(":smscode-core:verification"))
            api(project(":smscode-core:runtime"))
            implementation(libs.androidx.core.ktx)
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    // Kept deliberately: PlatformSerializable must stay an expect/actual classifier
    // (a common class cannot otherwise acquire java.io.Serializable), and Parcelize
    // serializes Sender.activeSchedule through it. KT-61573 keeps that pattern in
    // Beta, so the warning is suppressed here instead of being fixed away.
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}
