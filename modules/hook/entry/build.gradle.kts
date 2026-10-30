plugins {
    id("magisk.android.library")
    id("magisk.android.distribution")
}

val allowConflictBypass = findProperty("allowConflictBypass")
    ?.toString()
    ?.toBooleanStrictOrNull()
    ?: false

val magiskIsCiBuild: Boolean = (providers.gradleProperty("isCiBuild").orNull
    ?: providers.environmentVariable("MAGISK_IS_CI_BUILD").orNull)?.toBooleanStrictOrNull() ?: false

android {
    namespace = "io.github.magisk317.relay.hookentry"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "APPLICATION_ID", "\"io.github.magisk317.xinyi.relay\"")
        buildConfigField("String", "LOG_TAG", "\"xinyi\"")
        buildConfigField("boolean", "LOG_TO_XPOSED", "true")
        buildConfigField("String", "VERSION_NAME", "\"${libs.versions.versionName.get()}\"")
        buildConfigField("int", "VERSION_CODE", libs.versions.versionCode.get())
        buildConfigField("int", "MODULE_VERSION", libs.versions.versionCode.get())
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", allowConflictBypass.toString())
    }

    buildTypes {
        release {
            // Release stays at WARN so the e/w ring buffer remains exportable.
            // CI builds (-PisCiBuild=true / MAGISK_IS_CI_BUILD=true) drop to
            // VERBOSE so pipeline artifacts carry usable diagnostics.
            buildConfigField("int", "LOG_LEVEL", if (magiskIsCiBuild) "2" else "5")
        }
        debug {
            buildConfigField("int", "LOG_LEVEL", "2")
        }
    }
}

dependencies {
    implementation(project(":magisk-xposed-kit:runtime"))
    implementation(project(":magisk-xposed-kit:contract"))
    compileOnly(libs.libxposed.api)

    implementation(project(":xpbridge:core"))
    implementation(project(":magisk-forward-core:runtime"))
    implementation(project(":runtime"))
    implementation(project(":relay:android"))
    implementation(project(":relay:contract"))
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:rule"))
    implementation(project(":smscode-core:runtime"))
    implementation(project(":smscode-core:verification"))
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":magisk-xposed-kit"))
    implementation(project(":magisk-xposed-kit:capability"))
    implementation(project(":smscode-core:hook"))
    implementation(project(":magisk-xposed-kit:capture"))
    implementation(project(":magisk-forward-core:capture"))
    implementation(project(":magisk-xposed-kit:keepalive"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.libxposed.service)
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
}
