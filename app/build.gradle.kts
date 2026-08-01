plugins {
    id("magisk.android.application")
    id("relay.android.common")
    alias(libs.plugins.google.services)
    id("magisk.app.signing")
    id("magisk.app.packaging")
}

val versionNameStr = libs.versions.versionName.get()
val versionCodeInt = libs.versions.versionCode.get().toInt()
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
    into(generatedSmsCodeRulesAssetsDir.map { it.dir("smscode-rules") })
}

val verifyBundledSmsCodeRules = tasks.register("verifyBundledSmsCodeRules") {
    group = "verification"
    description = "Verify the generated APK assets match the smscode-core bundled rule contract."
    dependsOn(syncSmsCodeRulesAssets)
    val generatedRoot = generatedSmsCodeRulesAssetsDir.map { it.dir("smscode-rules") }
    inputs.dir(generatedRoot)
    doLast {
        val root = generatedRoot.get().asFile
        check(root.resolve("meta/rules-index.json").isFile) {
            "Bundled SMS code rules index is missing from smscode-rules/meta/rules-index.json"
        }
        check(root.resolve("rules").isDirectory) {
            "Bundled SMS code rules directory is missing from smscode-rules/rules"
        }
    }
}

android {
    namespace = "io.github.magisk317.relay"
    ndkVersion = ndkVersionStr

    // Dynamic Feature Module for E2EE is only used by Play variants.
    // GitHubWithE2ee bundles the native lib directly in the sender module.
    // AGP exposes dynamicFeatures as a global application setting, not a
    // per-variant switch. Keep the DFM attached only for dedicated Play bundle
    // or Play assemble invocations so mixed GitHub+Play validation commands do
    // not force GitHub variants to resolve Play-only feature variants.
    val requestedTasks = gradle.startParameter.taskNames
    val requestedAppTasks = requestedTasks.map { it.substringAfterLast(':') }
    val requestsPlayDynamicFeature = requestedAppTasks.any { taskName ->
        val normalized = taskName.substringAfterLast(':')
        normalized.contains("bundlePlay", ignoreCase = true) ||
            normalized.contains("packagePlay", ignoreCase = true) ||
            normalized.contains("assemblePlay", ignoreCase = true) ||
            normalized == "bundleRelease"
    }
    val requestsGithubVariant = requestedAppTasks.any { it.contains("Github", ignoreCase = true) }
    if (requestsPlayDynamicFeature && !requestsGithubVariant) {
        dynamicFeatures += ":features:matrix_e2ee"
    }

    androidResources {
        localeFilters.addAll(listOf("en", "zh-rCN", "zh-rTW"))
    }

    defaultConfig {
        applicationId = "io.github.magisk317.xinyi.relay"

        versionCode = versionCodeInt
        versionName = versionNameStr

        buildConfigField("String", "LOG_TAG", "\"relay\"")
        buildConfigField("int", "MODULE_VERSION", "$versionCodeInt")
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", allowConflictBypass.toString())
    }

    productFlavors {
        getByName("play") {
            buildConfigField("boolean", "ENABLE_STANDARD_MODE_SERVICE", "false")
        }
        listOf("githubNoE2ee", "githubWithE2ee", "fdroid").forEach { flavorName ->
            getByName(flavorName) {
                buildConfigField("boolean", "ENABLE_STANDARD_MODE_SERVICE", "true")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(generatedSmsCodeRulesAssetsDir.get().asFile.path)
        }
        getByName("play") {
            java.directories.add("src/xposed/java")
            kotlin.directories.add("src/xposed/java")
        }
        getByName("githubNoE2ee") {
            setRoot("src/github")
            java.directories.add("src/xposed/java")
            kotlin.directories.add("src/xposed/java")
        }
        getByName("githubWithE2ee") {
            setRoot("src/github")
            java.directories.add("src/xposed/java")
            kotlin.directories.add("src/xposed/java")
        }
        getByName("fdroid") {
            java.directories.add("src/xposed/java")
            kotlin.directories.add("src/xposed/java")
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
    dependsOn(verifyBundledSmsCodeRules)
}

tasks.matching { it.name.endsWith("GoogleServices") }.configureEach {
    val shouldDisableForFdroid = name.startsWith("processFdroid")
    if (skipGoogleServices || shouldDisableForFdroid) {
        enabled = false
    }
}

dependencies {
    implementation(project(":policy"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))
    implementation(project(":mobile:ui"))
    implementation(project(":relay:android"))
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":smscode-core:runtime"))
    implementation(project(":smscode-core:verification"))
    implementation(project(":relay:engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)

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

    listOf("play", "githubNoE2ee", "githubWithE2ee", "fdroid").forEach { flavor ->
        add("${flavor}Implementation", project(":hook:entry"))
        add("${flavor}Implementation", project(":xpbridge:core"))
        add("${flavor}Implementation", libs.libxposed.service)
    }

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
