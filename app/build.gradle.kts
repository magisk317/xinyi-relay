plugins {
    id("magisk.android.application")
    id("relay.android.common")
    alias(libs.plugins.google.services)
    id("magisk.app.signing")
    id("magisk.app.packaging")
}

val versionNameStr = libs.versions.versionName.get()
val versionCodeInt = libs.versions.versionCode.get().toInt()
val minSdkStr = libs.versions.minSdk.get()
val targetSdkStr = libs.versions.targetSdk.get()
val ndkVersionStr = libs.versions.ndk.get()
val allowConflictBypass = findProperty("allowConflictBypass")
    ?.toString()
    ?.toBooleanStrictOrNull()
    ?: false
val skipGoogleServices = findProperty("skipGoogleServices")
    ?.toString()
    ?.toBooleanStrictOrNull()
    ?: false
val generatedSmsCodeRulesAssetsDir = layout.buildDirectory.dir("generated/smscodeRulesAssets")
val syncSmsCodeRulesAssets = tasks.register<Sync>("syncSmsCodeRulesAssets") {
    val rulesRoot = rootProject.layout.projectDirectory.dir("smscode/rules")
    from(rulesRoot.dir("_meta")) {
        into("meta")
    }
    from(rulesRoot.dir("rules")) {
        into("rules")
    }
    into(generatedSmsCodeRulesAssetsDir.map { it.dir("smscode/rules") })
}

android {
    namespace = "io.github.magisk317.relay"
    ndkVersion = ndkVersionStr

    // Dynamic Feature Module for E2EE is only used by Play variants.
    // GitHubWithE2ee bundles the native lib directly in the sender module.
    // Keep the DFM attached only for explicit Play bundle/build entrypoints so
    // GitHub tasks do not resolve Play-only feature variants.
    val requestedTasks = gradle.startParameter.taskNames
    val isPlayBuild = requestedTasks.any { taskName ->
        val normalized = taskName.substringAfterLast(':')
        normalized.contains("bundlePlay", ignoreCase = true) ||
            normalized.contains("packagePlay", ignoreCase = true) ||
            normalized.contains("assemblePlay", ignoreCase = true) ||
            normalized == "bundleRelease"
    }
    if (isPlayBuild) {
        dynamicFeatures += ":features:matrix_e2ee"
    }

    androidResources {
        localeFilters.addAll(listOf("en", "zh-rCN", "zh-rTW"))
    }

    defaultConfig {
        applicationId = "io.github.magisk317.xinyi.relay"

        val minSdkCodename = minSdkStr.removePrefix("android-")
        val minSdkAsInt = minSdkCodename.toIntOrNull()
        if (minSdkAsInt != null) {
            minSdk = minSdkAsInt
        } else {
            @Suppress("DEPRECATION")
            minSdkPreview = minSdkCodename
        }

        val targetSdkCodename = targetSdkStr.removePrefix("android-")
        val targetSdkAsInt = targetSdkCodename.toIntOrNull()
        if (targetSdkAsInt != null) {
            targetSdk = targetSdkAsInt
        } else {
            targetSdkPreview = targetSdkCodename
        }

        versionCode = versionCodeInt
        versionName = versionNameStr

        buildConfigField("String", "LOG_TAG", "\"relay\"")
        buildConfigField("int", "MODULE_VERSION", "$versionCodeInt")
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", allowConflictBypass.toString())
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(generatedSmsCodeRulesAssetsDir.get().asFile.path)
        }
        getByName("githubNoE2ee") {
            setRoot("src/github")
        }
        getByName("githubWithE2ee") {
            setRoot("src/github")
        }
    }

    packaging {
        resources {
            excludes += "**/*.kotlin_*"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            merges += "META-INF/xposed/*"
        }
    }

}

tasks.named("preBuild") {
    dependsOn(syncSmsCodeRulesAssets)
}

tasks.matching { it.name.endsWith("GoogleServices") }.configureEach {
    val shouldDisableForFdroid = name.startsWith("processFdroid")
    if (skipGoogleServices || shouldDisableForFdroid) {
        enabled = false
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":hook:entry"))
    implementation(project(":core"))
    implementation(project(":mobile:ui"))
    implementation(project(":relay:android"))
    implementation(project(":xpbridge:core"))
    implementation(project(":smscode-core:verification"))
    implementation(project(":smscode-core:hook"))
    implementation(project(":runtime"))
    implementation(project(":relay:engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.libxposed.service)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
    implementation(libs.timber)

    add("playImplementation", platform(libs.firebase.bom))
    add("playImplementation", libs.firebase.analytics)
    add("githubNoE2eeImplementation", platform(libs.firebase.bom))
    add("githubNoE2eeImplementation", libs.firebase.analytics)
    add("githubWithE2eeImplementation", platform(libs.firebase.bom))
    add("githubWithE2eeImplementation", libs.firebase.analytics)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val verifyNoRuntimePipelineLeak = tasks.register("verifyNoRuntimePipelineLeak") {
    group = "verification"
    description = "Ensure the app shell does not directly depend on runtime/bootstrap/domain/platform implementation packages."

    val sourceRoot = layout.projectDirectory.dir("src/main/java")
    val projectRoot = layout.projectDirectory.asFile
    val bannedRegexes = listOf(
        Regex("""^\s*import\s+io\.github\.magisk317\.relay\.(bootstrap|data|domain|platform|prefs)\."""),
    )

    inputs.dir(sourceRoot)

    doLast {
        val violations = sourceRoot
            .asFileTree
            .matching { include("**/*.kt") }
            .files
            .flatMap { source ->
                source.readLines().mapIndexedNotNull { index, line ->
                    if (bannedRegexes.any { it.containsMatchIn(line) }) {
                        "${source.relativeTo(projectRoot)}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("App must not directly depend on runtime/bootstrap/domain/platform implementation packages:")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyNoRuntimePipelineLeak)
}

// Fix JUnit 5 test discovery
tasks.withType<Test> {
    useJUnitPlatform()
}
