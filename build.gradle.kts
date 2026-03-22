import dev.detekt.gradle.extensions.DetektExtension
import com.adarshr.gradle.testlogger.theme.ThemeType
import org.gradle.api.tasks.Exec

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    configurations.all {
        resolutionStrategy {
            force(libs.jose4j)
            force(libs.jdom2)
            force(libs.apache.commons.lang3)
        }
    }
}

plugins {
    alias(libs.plugins.version.catalog.update)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.test.logger) apply false
}

kover {
    reports {
        verify {
            rule {
                // Start with a pragmatic threshold and tighten later.
                minBound(60)
            }
        }
    }
}

val catalog = libs

subprojects {
    fun Project.configureDetekt() {
        apply(plugin = "dev.detekt")
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            autoCorrect = true
            parallel = true
            buildUponDefaultConfig = false
            config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
        }
        dependencies {
            "detektPlugins"(catalog.detekt.rules.ktlint)
        }
    }

    // Apply kover to all projects
    apply(plugin = "org.jetbrains.kotlinx.kover")

    pluginManager.withPlugin("com.android.application") {
        configureDetekt()
        apply(plugin = "com.adarshr.test-logger")
    }
    pluginManager.withPlugin("com.android.library") {
        configureDetekt()
        apply(plugin = "com.adarshr.test-logger")
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        configureDetekt()
        apply(plugin = "com.adarshr.test-logger")
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        configureDetekt()
        apply(plugin = "com.adarshr.test-logger")
    }

    // Configure test-logger for all projects
    plugins.withId("com.adarshr.test-logger") {
        configure<com.adarshr.gradle.testlogger.TestLoggerExtension> {
            theme = ThemeType.MOCHA
            showExceptions = true
            showStackTraces = true
            showCauses = true
            showSummary = true
        }
    }

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }

    configurations.all {
        resolutionStrategy {
            force(catalog.jose4j)
            force(catalog.jdom2)
            force(catalog.apache.commons.lang3)
            force(catalog.apache.httpclient)
            force(catalog.netty.codec.http)
            force(catalog.netty.codec)
            force(catalog.netty.codec.http2)
            force(catalog.netty.common)
            force(catalog.netty.handler)
            force(catalog.netty.resolver)
            force(catalog.netty.transport)
            force(catalog.netty.buffer)
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            // END AUTO FORCED DEPENDENCIES (managed by workflow)
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.register<Exec>("cleanupGradleCaches") {
    group = "maintenance"
    description = "Remove stale Gradle version caches under the project-local .gradle directory."
    workingDir = rootProject.projectDir
    commandLine("bash", "${rootProject.projectDir}/scripts/cleanup_gradle_caches.sh")
}

tasks.register<Exec>("verifyModuleBoundaries") {
    group = "verification"
    description = "Ensure app/core/runtime follow the intended direct project dependency graph."
    workingDir = rootProject.projectDir
    commandLine("bash", "${rootProject.projectDir}/scripts/verify_module_boundaries.sh")
}

tasks.register<Exec>("verifyStructureBoundaries") {
    group = "verification"
    description = "Ensure app keeps only entry-layer sources and moved logic stays out."
    workingDir = rootProject.projectDir
    commandLine("bash", "${rootProject.projectDir}/scripts/verify_structure_boundaries.sh")
}

tasks.register<Exec>("verifyEmbeddedSubmodules") {
    group = "verification"
    description = "Ensure embedded submodules stay minimal and do not regrow into parallel root builds."
    workingDir = rootProject.projectDir
    commandLine("bash", "${rootProject.projectDir}/scripts/verify_embedded_submodules.sh")
}
