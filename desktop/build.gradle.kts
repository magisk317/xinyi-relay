plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
}

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.desktop.material3)
            implementation(libs.compose.desktop.material.icons.extended)
            implementation(project(":relay:contract"))
            implementation(project(":relay:sender:api"))
            implementation(project(":relay:net"))
            implementation(project(":desktop:core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okhttp)
            implementation(libs.zxing.core)
        }
        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnitPlatform()
}

/**
 * Version and package identity of the KMP desktop track.
 *
 * `versionName` in the catalog is the single source of truth for the Android
 * app, and reading it here is what keeps the desktop package from answering a
 * different version number than the app for the same release.
 *
 * `desktopVersion` overrides it for release builds, which must strip a
 * pre-release suffix: JDK tooling rejects the Debian-style revisions a suffix
 * would otherwise turn into.
 */
val desktopVersion: String = providers.gradleProperty("desktopVersion")
    .orNull ?: libs.versions.versionName.get()

/**
 * Package formats produced by the build host.
 *
 * The default is one format per operating system because the plugin points every
 * format of an OS at the same app-image directory and declares no dependency
 * between them, so requesting two Linux formats at once fails validation on
 * Gradle 9 with an implicit-dependency error. `-PdesktopTargetFormats=Rpm,AppImage`
 * overrides the set when a release needs the other formats.
 */
val desktopTargetFormats: List<TargetFormat> = providers.gradleProperty("desktopTargetFormats")
    .orNull
    ?.split(",")
    ?.map { format -> format.trim() }
    ?.filter { format -> format.isNotEmpty() }
    ?.map { format -> TargetFormat.valueOf(format) }
    ?: when (System.getProperty("os.name")) {
        "Linux" -> listOf(TargetFormat.Deb)
        "Mac OS X" -> listOf(TargetFormat.Dmg)
        else -> listOf(TargetFormat.Exe)
    }

compose.desktop {
    application {
        mainClass = "io.github.magisk317.relay.desktop.MainKt"

        // appResourcesRootDir must point at jvmMain: the plugin's own default is
        // src/main/resources, which a KMP module never populates, so the tray
        // icon would silently end up in no package at all.
        nativeDistributions {
            appResourcesRootDir.set(layout.projectDirectory.dir("src/jvmMain/resources"))
            packageName = providers.gradleProperty("desktopPackageName")
                .orNull ?: "xinyi-relay-desktop-kmp"
            packageVersion = desktopVersion
            description = "Xinyi Relay Desktop (Kotlin Multiplatform)"
            vendor = "magisk317"
            copyright = "© 2026 magisk317"

            modules("jdk.httpserver")
            targetFormats(*desktopTargetFormats.toTypedArray())
        }
    }
}
