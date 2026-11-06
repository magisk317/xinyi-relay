plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
}

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val desktopDiagnosticBuild = providers.gradleProperty("isCiBuild")
    .orElse(providers.environmentVariable("MAGISK_IS_CI_BUILD"))
    .map { value -> value.toBooleanStrictOrNull() ?: false }
    .orElse(!providers.gradleProperty("desktopVersion").isPresent)
val generatedDesktopBuildConfig = layout.buildDirectory.dir("generated/source/desktopBuildConfig")
/**
 * A custom task class keeps this task configuration-cache clean: a registered
 * task with a doLast closure captures Gradle script object references, which
 * fails configuration cache store on cold builds (first seen on the tag
 * pipeline's desktop packaging jobs, 2026-11-06; local gates run with
 * org.gradle.configuration-cache=false via the shared global gradle
 * properties, so only CI exposes the violation).
 */
abstract class GenerateDesktopBuildConfigTask : DefaultTask() {
    @get:Input
    abstract val diagnosticBuild: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageDir = outputDir.get().dir("io/github/magisk317/relay/desktop").asFile
        packageDir.mkdirs()
        packageDir.resolve("DesktopBuildConfig.kt").writeText(
            """
            package io.github.magisk317.relay.desktop

            internal object DesktopBuildConfig {
                const val DIAGNOSTIC_LOGGING: Boolean = ${diagnosticBuild.get()}
            }
            """.trimIndent() + "\n",
        )
    }
}

val generateDesktopBuildConfig = tasks.register<GenerateDesktopBuildConfigTask>("generateDesktopBuildConfig") {
    diagnosticBuild.set(desktopDiagnosticBuild)
    outputDir.set(generatedDesktopBuildConfig)
}

kotlin {
    jvm()

    sourceSets {
        named("jvmMain") {
            kotlin.srcDir(generatedDesktopBuildConfig)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.desktop.material3)
            implementation(libs.compose.desktop.material.icons.extended)
            implementation(project(":relay:contract"))
            implementation(project(":magisk-forward-core:sender:api"))
            implementation(project(":magisk-forward-core:net"))
            implementation(project(":desktop:core"))
            implementation(project(":magisk-xposed-kit:logging"))
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

tasks.named("compileKotlinJvm").configure {
    dependsOn(generateDesktopBuildConfig)
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
