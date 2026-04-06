plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.mokkery)
    id("relay.android.common")
    id("relay.app.signing")
    id("relay.app.webui")
    id("relay.app.packaging")
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

android {
    namespace = "io.github.magisk317.relay"
    ndkVersion = ndkVersionStr

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

    packaging {
        resources {
            excludes += "**/*.kotlin_*"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            merges += "META-INF/xposed/*"
        }
    }

    val javaVersion = JavaVersion.toVersion(libs.versions.javaBytecode.get())
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion.toString()))
        }
    }
}

tasks.matching { it.name.endsWith("GoogleServices") }.configureEach {
    val shouldDisableForFdroid = name.startsWith("processFdroid")
    if (skipGoogleServices || shouldDisableForFdroid) {
        enabled = false
    }
}

mokkery {
    defaultMockMode.set(dev.mokkery.MockMode.autofill)
    ignoreFinalMembers.set(true)
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))
    implementation(project(":webui-core"))
    implementation(project(":xpbridge-core"))
    implementation(project(":smscode-core:smscode-domain"))
    implementation(project(":smscode-core:smscode-verification-core"))
    compileOnly(project(":smscode-core:smscode-xposed-core"))

    implementation(libs.androidx.core.ktx)
    add("legacyCompileOnly", project(":xposed-stub"))
    add("api101CompileOnly", libs.libxposed.api)
    add("api101Implementation", libs.libxposed.service)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
    implementation(libs.timber)

    add("playImplementation", platform(libs.firebase.bom))
    add("playImplementation", libs.firebase.analytics)
    add("githubImplementation", platform(libs.firebase.bom))
    add("githubImplementation", libs.firebase.analytics)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mokkery.runtime.jvm)
    testImplementation(project(":smscode-core:smscode-xposed-core"))
}

val verifyNoRuntimePipelineLeak by tasks.registering {
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
