plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    id("relay.android.common")
}

val minSdkInt = libs.versions.minSdk.get().toInt()
val allowConflictBypass = findProperty("allowConflictBypass")
    ?.toString()
    ?.toBooleanStrictOrNull()
    ?: false

android {
    namespace = "io.github.magisk317.relay.core"

    defaultConfig {
        minSdk = minSdkInt
        buildConfigField("int", "VERSION_CODE", libs.versions.versionCode.get())
        buildConfigField("String", "VERSION_NAME", "\"${libs.versions.versionName.get()}\"")
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", allowConflictBypass.toString())
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        disable.add("MissingTranslation")
        disable.add("LocalContextGetResourceValueCall")
        disable.add("NonObservableLocale")
    }

    val javaVersion = JavaVersion.toVersion(libs.versions.javaBytecode.get())
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion.toString()))
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val capitalizedVariant = variant.name.replaceFirstChar { it.uppercaseChar() }
        tasks.matching { it.name == "bundleLibCompileToJar$capitalizedVariant" }.configureEach {
            val builtInKotlinClasses =
                layout.buildDirectory.dir("intermediates/built_in_kotlinc/${variant.name}/compile${capitalizedVariant}Kotlin/classes")
            if (this is org.gradle.api.tasks.bundling.Zip) {
                from(builtInKotlinClasses)
            }
        }
    }
}

dependencies {
    implementation(project(":runtime"))
    implementation(project(":relay-engine"))
    implementation(project(":relay-android"))
    implementation(project(":relay-contract"))
    implementation(project(":xpbridge-core"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":smscode-core:smscode-domain"))
    implementation(project(":smscode-core:smscode-runtime-common"))
    implementation(project(":smscode-core:smscode-verification-core"))
    implementation(project(":smscode-core:smscode-xposed-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.gson)
    implementation(libs.androidx.room.runtime)
    
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.okhttp.tls)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.koin.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.timber)
    implementation(libs.kotlinx.collections.immutable)

    add("playImplementation", platform(libs.firebase.bom))
    add("playImplementation", libs.firebase.auth)
    add("playImplementation", libs.play.services.auth)
    add("playImplementation", libs.kotlinx.coroutines.play.services)
    add("playImplementation", libs.billing.ktx)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val verifyNoWebUiLeak by tasks.registering {
    group = "verification"
    description = "Ensure the core module does not directly retain embedded WebUI implementation packages."

    val sourceRoot = layout.projectDirectory.dir("src/main/java")
    val projectRoot = layout.projectDirectory.asFile
    val bannedRegexes = listOf(
        Regex("""^\s*import\s+io\.github\.magisk317\.relay\.webui\."""),
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
                    appendLine("Core must not directly depend on embedded WebUI implementation packages:")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyNoWebUiLeak)
}
