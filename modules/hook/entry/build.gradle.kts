plugins {
    id("magisk.android.library")
    id("relay.android.common")
}

val allowConflictBypass = findProperty("allowConflictBypass")
    ?.toString()
    ?.toBooleanStrictOrNull()
    ?: false

android {
    namespace = "io.github.magisk317.relay.hookentry"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "APPLICATION_ID", "\"io.github.magisk317.xinyi.relay\"")
        buildConfigField("String", "LOG_TAG", "\"xinyi\"")
        buildConfigField("int", "LOG_LEVEL", "2")
        buildConfigField("boolean", "LOG_TO_XPOSED", "true")
        buildConfigField("String", "VERSION_NAME", "\"${libs.versions.versionName.get()}\"")
        buildConfigField("int", "VERSION_CODE", libs.versions.versionCode.get())
        buildConfigField("int", "MODULE_VERSION", libs.versions.versionCode.get())
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", allowConflictBypass.toString())
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    compileOnly(project(":smscode-core:hook"))

    implementation(project(":xpbridge:core"))
    implementation(project(":runtime"))
    implementation(project(":relay:android"))
    implementation(project(":relay:contract"))
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:rule"))
    implementation(project(":smscode-core:runtime"))
    implementation(project(":smscode-core:verification"))
    implementation(project(":magisk-xposed-kit:logging"))
    implementation(project(":magisk-xposed-kit"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.libxposed.service)
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":smscode-core:hook"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
}
