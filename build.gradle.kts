import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Exec
buildscript {
    configurations.all {
        resolutionStrategy {
            // Java 27 bytecode target: AGP 9.4.1 bundles ASM 9.9 (V26 max) and
            // rejects major 71. ASM 9.10.1 adds V27; force the family here because
            // this is the classpath AGP actually runs on (project-level forces do
            // not reach the plugin classpath). Deliberately outside the managed
            // block: the dependency-force workflow rewrites that block wholesale
            // and would drop the comment on its next run.
            force("org.ow2.asm:asm:9.11.1")
            force("org.ow2.asm:asm-analysis:9.11.1")
            force("org.ow2.asm:asm-commons:9.11.1")
            force("org.ow2.asm:asm-tree:9.11.1")
            force("org.ow2.asm:asm-util:9.11.1")
        }
    }
}


// Java 27 bytecode target, lint side: AGP 9.5.0-rc01's lint-api (32.5.0-rc01)
// resolves ASM 9.9 (V26 max) on androidLintTool, so lintAnalyze skips every
// major-71 class with "broken class file?" warnings while the job stays green.
// The buildscript force above does not reach project configurations; force the
// same family here until the lint toolchain ships ASM >= 9.10 itself.
allprojects {
    configurations.configureEach {
        if (name == "androidLintTool") {
            resolutionStrategy {
                force("org.ow2.asm:asm:9.11.1")
                force("org.ow2.asm:asm-analysis:9.11.1")
                force("org.ow2.asm:asm-commons:9.11.1")
                force("org.ow2.asm:asm-tree:9.11.1")
                force("org.ow2.asm:asm-util:9.11.1")
            }
        }
    }
}
plugins {
    alias(libs.plugins.version.catalog.update)
    id("magisk.android.application") apply false
    id("magisk.android.library") apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    id("magisk.android.compose") apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
    id("magisk.maintenance")
    id("magisk.distribution.policy")
}

// Which distributions this app ships as and what each one compiles in. This is
// product data, so it belongs here rather than in the shared convention: the
// convention owns only the mechanism that turns this table into AGP flavors.
//
// Play and GitHub are the active channels. F-Droid is paused - the commented
// flavor in each channel block is the single resume point, so a paused
// distribution stays a documented state instead of becoming a flavor that was
// quietly dropped.
magiskDistribution {
    flavor("play") {
        buildConfigBoolean("ENABLE_SMS_CHANNEL", false)
        buildConfigBoolean("ALLOW_HTTP_WEBHOOK", true)
        buildConfigBoolean("ENABLE_ACCESSIBILITY_AUTO_INPUT", false)
        buildConfigBoolean("HAS_BILLING", true)
        buildConfigBoolean("HAS_CLOUD_BACKUP", true)
        buildConfigBoolean("HAS_MATRIX_E2EE", true)
    }
    flavor("github") {
        buildConfigBoolean("ENABLE_SMS_CHANNEL", true)
        buildConfigBoolean("ALLOW_HTTP_WEBHOOK", true)
        buildConfigBoolean("ENABLE_ACCESSIBILITY_AUTO_INPUT", true)
        buildConfigBoolean("HAS_BILLING", false)
        buildConfigBoolean("HAS_CLOUD_BACKUP", false)
        // The GitHub flavor ships no E2EE code: the matrix-rust-sdk runtime arrives
        // as a separately versioned plugin APK (features/matrix-e2ee-plugin) that
        // is downloaded and DexClassLoader-loaded at runtime. The app only pins
        // the upstream SDK version it expects the plugin to carry.
        buildConfigBoolean("HAS_MATRIX_E2EE", false)
        buildConfigVersion("MATRIX_PLUGIN_SDK_VERSION", "matrix-sdk-android")
    }
    // Play Feature Delivery is a store capability, so only the Play flavor builds
    // dynamic-feature variants. The others stay disabled: a plain root assembleDebug
    // would otherwise schedule their manifest merge, which fails on the empty feature
    // metadata.
    dynamicFeatureFlavors.add("play")
    preferInjectedCommitHash = true
}


val catalog = libs
val enableKover = providers.gradleProperty("enableKover")
    .map { it.toBooleanStrictOrNull() ?: false }
    .getOrElse(false) ||
    gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("kover", ignoreCase = true)
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
        // detekt CLI whitelists JVM targets and 2.0.0-alpha.6 caps at 26, so the
        // analysis target must not exceed that ceiling even though we emit Java 27
        // bytecode (the compile target tracks the Gradle daemon JVM, which can be
        // newer than the bytecode we emit). Revisit when detekt ships V27 support.
        tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
            jvmTarget.set(minOf(catalog.versions.javaBytecode.get().toInt(), 26).toString())
        }
        tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
            jvmTarget.set(minOf(catalog.versions.javaBytecode.get().toInt(), 26).toString())
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
    // Multiplatform modules (the kit's logging/keepalive, magisk-ui-kit and the
    // desktop modules) matched none of the hooks above, so they got no detekt task at all and
    // their findings were never computed. Same treatment as the Android modules: analysed, and
    // non-blocking until each one is measured.
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
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
    description = "Ensure app/mobile:core/runtime follow the intended direct project dependency graph."
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

tasks.register<Exec>("verifyNoGlobalWarningMute") {
    group = "verification"
    description = "Ensure the Kotlin discarded-result diagnostics stay enabled; tolerated failures must be explicit at the call site."
    workingDir = rootProject.projectDir
    commandLine("bash", "${rootProject.projectDir}/scripts/checks/verify_no_global_warning_mute.sh")
}

tasks.register("check") {
    group = "verification"
    description = "Run root project architecture and localized dependency governance checks."
    dependsOn(
        "verifyModuleBoundaries",
        "verifyStructureBoundaries",
        "verifyEmbeddedSubmodules",
        "verifyDependencyGovernance",
        "verifyNoGlobalWarningMute",
    )
}


