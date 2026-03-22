plugins {
    alias(libs.plugins.android.library)
    id(libs.plugins.kotlin.serialization.get().pluginId)
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    alias(libs.plugins.ksp)
}

apply(from = rootProject.file("gradle/relay-android-common.gradle"))

android {
    namespace = "io.github.magisk317.relay.runtime"

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
        }
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        buildConfigField("String", "LOG_TAG", "\"relay\"")
        buildConfigField("String", "APPLICATION_ID", "\"io.github.magisk317.xinyi.relay\"")
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            buildConfigField("int", "LOG_LEVEL", "4")
            buildConfigField("boolean", "LOG_TO_XPOSED", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("int", "LOG_LEVEL", "2")
            buildConfigField("boolean", "LOG_TO_XPOSED", "true")
        }
    }

    val javaVersion = JavaVersion.toVersion(libs.versions.javaBytecode.get())
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion.toString()))
        }
    }
}

dependencies {
    implementation(project(":smscode-core:smscode-domain"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.gson)
    
    // Networking (needed by GithubUpdateChecker)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.jakarta.mail)
    implementation(libs.paho.mqtt)
    
    // Database (Room)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    implementation(libs.timber)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
}

val verifyNoComposeUiLeak by tasks.registering {
    group = "verification"
    description = "Ensure the runtime module does not pick up Compose UI dependencies."

    val sourceRoot = layout.projectDirectory.dir("src/main/java")
    val projectRoot = layout.projectDirectory.asFile
    val bannedRegexes = listOf(
        Regex("""^\s*import\s+androidx\.compose\.(?!runtime\.Immutable)"""),
        Regex("""@\s*Composable\b"""),
        Regex("""\bMaterialTheme\b"""),
        Regex("""\bModifier\b"""),
    )

    inputs.dir(sourceRoot)

    doLast {
        val violations = sourceRoot
            .asFileTree
            .matching { include("**/*.kt") }
            .files
            .flatMap { source ->
                source.readLines().mapIndexedNotNull { index, line ->
                    if (bannedRegexes.any { regex -> regex.containsMatchIn(line) }) {
                        "${source.relativeTo(projectRoot)}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Compose UI APIs are not allowed in the runtime module:")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyNoComposeUiLeak)
}
