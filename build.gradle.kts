import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Exec
buildscript {
    configurations.all {
        resolutionStrategy {
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            force("com.google.code.gson:gson:2.14.0")
            force("com.google.guava:guava:33.6.0-jre")
            force("io.netty:netty-codec:5.0.0.Alpha2")
            force("io.netty:netty-codec-http:5.0.0.Alpha2")
            force("io.netty:netty-codec-http2:5.0.0.Alpha2")
            force("io.netty:netty-common:5.0.0.Alpha2")
            force("io.netty:netty-handler:5.0.0.Alpha2")
            force("io.netty:netty-handler-proxy:5.0.0.Alpha2")
            force("org.apache.commons:commons-lang3:3.20.0")
            force("org.apache.httpcomponents:httpclient:4.5.14")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcprov-jdk18on:1.85")
            force("org.jdom:jdom2:2.0.6.1")
            // END AUTO FORCED DEPENDENCIES (managed by workflow)

            // Java 27 bytecode target: AGP 9.4.1 bundles ASM 9.9 (V26 max) and
            // rejects major 71. ASM 9.10.1 adds V27; force the family here because
            // this is the classpath AGP actually runs on (project-level forces do
            // not reach the plugin classpath). Deliberately outside the managed
            // block: the dependency-force workflow rewrites that block wholesale
            // and would drop the comment on its next run.
            force("org.ow2.asm:asm:9.10.1")
            force("org.ow2.asm:asm-analysis:9.10.1")
            force("org.ow2.asm:asm-commons:9.10.1")
            force("org.ow2.asm:asm-tree:9.10.1")
            force("org.ow2.asm:asm-util:9.10.1")
        }
    }
}

plugins {
    alias(libs.plugins.version.catalog.update)
    id("magisk.android.application") apply false
    id("magisk.android.library") apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    id("magisk.android.compose") apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    id("magisk.maintenance")
}

val catalog = libs
val enableKover = providers.gradleProperty("enableKover")
    .map { it.toBooleanStrictOrNull() ?: false }
    .getOrElse(false) ||
    gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("kover", ignoreCase = true)
    }

val forcedKotlinVersion = extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("kotlin")
    .get()
    .requiredVersion

allprojects {
    configurations.configureEach {
        resolutionStrategy {
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            force("com.google.code.gson:gson:2.14.0")
            force("com.google.guava:guava:33.6.0-jre")
            force("io.netty:netty-codec:5.0.0.Alpha2")
            force("io.netty:netty-codec-http:5.0.0.Alpha2")
            force("io.netty:netty-codec-http2:5.0.0.Alpha2")
            force("io.netty:netty-common:5.0.0.Alpha2")
            force("io.netty:netty-handler:5.0.0.Alpha2")
            force("io.netty:netty-handler-proxy:5.0.0.Alpha2")
            force("org.apache.commons:commons-lang3:3.20.0")
            force("org.apache.httpcomponents:httpclient:4.5.14")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.bouncycastle:bcpkix-jdk18on:1.84")
            force("org.bouncycastle:bcprov-jdk18on:1.85")
            force("org.jdom:jdom2:2.0.6.1")
            // END AUTO FORCED DEPENDENCIES (managed by workflow)

            // Custom migration overrides for Java 27 compatibility
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:$forcedKotlinVersion")
            force("org.ow2.asm:asm:9.10.1")
            force("org.ow2.asm:asm-commons:9.10.1")
            force("org.ow2.asm:asm-tree:9.10.1")
            force("org.ow2.asm:asm-analysis:9.10.1")
            force("org.ow2.asm:asm-util:9.10.1")
        }
    }
}

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

    // Kover is report-only: keep HTML/XML coverage artifacts, never fail the build on thresholds.
    if (enableKover) {
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }

    pluginManager.withPlugin("com.android.application") {
        configureDetekt()
    }
    pluginManager.withPlugin("com.android.library") {
        configureDetekt()
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        configureDetekt()
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        configureDetekt()
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
    commandLine("bash", "${rootProject.projectDir}/scripts/checks/verify_module_boundaries.sh")
}

tasks.register<Exec>("verifyStructureBoundaries") {
    group = "verification"
    description = "Ensure app keeps only entry-layer sources and moved logic stays out."
    workingDir = rootProject.projectDir
    commandLine("bash", "${rootProject.projectDir}/scripts/checks/verify_structure_boundaries.sh")
}

tasks.register<Exec>("verifyEmbeddedSubmodules") {
    group = "verification"
    description = "Ensure embedded submodules stay minimal and do not regrow into parallel root builds."
    workingDir = rootProject.projectDir
    commandLine("bash", "${rootProject.projectDir}/scripts/checks/verify_embedded_submodules.sh")
}

tasks.register<Exec>("verifyDependencyGovernance") {
    group = "verification"
    description = "Ensure dependency repositories stay centralized and force rules stay localized in the root build."
    workingDir = rootProject.projectDir
    commandLine("bash", "${rootProject.projectDir}/scripts/checks/verify_dependency_governance.sh")
}

tasks.register("check") {
    group = "verification"
    description = "Run root project architecture and localized dependency governance checks."
    dependsOn(
        "verifyModuleBoundaries",
        "verifyStructureBoundaries",
        "verifyEmbeddedSubmodules",
        "verifyDependencyGovernance",
    )
}
