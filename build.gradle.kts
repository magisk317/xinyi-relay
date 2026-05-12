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
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            force("com.google.code.gson:gson:2.14.0")
            force("com.google.guava:guava:33.6.0-jre")
            force("io.netty:netty-codec:4.1.133.Final")
            force("io.netty:netty-codec-http:4.1.133.Final")
            force("io.netty:netty-codec-http2:4.1.133.Final")
            force("io.netty:netty-common:4.1.118.Final")
            force("io.netty:netty-handler:4.1.118.Final")
            force("io.netty:netty-handler-proxy:4.1.133.Final")
            force("org.apache.commons:commons-lang3:3.20.0")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcprov-jdk18on:1.84")
            force("org.jdom:jdom2:2.0.6.1")
            // END AUTO FORCED DEPENDENCIES (managed by workflow)
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
    id("magisk.maintenance")
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
            force(catalog.apache.httpclient)
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            force("com.google.code.gson:gson:2.14.0")
            force("com.google.guava:guava:33.6.0-jre")
            force("io.netty:netty-codec:4.1.133.Final")
            force("io.netty:netty-codec-http:4.1.133.Final")
            force("io.netty:netty-codec-http2:4.1.133.Final")
            force("io.netty:netty-common:4.1.118.Final")
            force("io.netty:netty-handler:4.1.118.Final")
            force("io.netty:netty-handler-proxy:4.1.133.Final")
            force("org.apache.commons:commons-lang3:3.20.0")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcprov-jdk18on:1.84")
            force("org.jdom:jdom2:2.0.6.1")
            // END AUTO FORCED DEPENDENCIES (managed by workflow)
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Maintenance task now automatically hooked via magisk.maintenance plugin

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
