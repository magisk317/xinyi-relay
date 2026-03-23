plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
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
            excludes += "META-INF/io.netty.versions.properties"
        }
    }

    val javaVersion = JavaVersion.toVersion(libs.versions.javaBytecode.get())
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion.toString()))
        }
    }
}

tasks.matching {
    it.name.startsWith("processFdroid") && it.name.endsWith("GoogleServices")
}.configureEach {
    enabled = false
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))
    compileOnly(project(":smscode-core:smscode-xposed-core"))

    implementation(libs.androidx.core.ktx)
    compileOnly(libs.libxposed.api)

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
    testImplementation(libs.mockk)
    testImplementation(project(":smscode-core:smscode-xposed-core"))
}
