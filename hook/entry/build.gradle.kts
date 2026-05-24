plugins {
    alias(libs.plugins.android.library)
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.hookentry"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "APPLICATION_ID", "\"io.github.magisk317.xinyi.relay\"")
        buildConfigField("String", "LOG_TAG", "\"relay\"")
        buildConfigField("int", "LOG_LEVEL", "2")
        buildConfigField("boolean", "LOG_TO_XPOSED", "true")
        buildConfigField("String", "VERSION_NAME", "\"${libs.versions.versionName.get()}\"")
        buildConfigField("int", "VERSION_CODE", libs.versions.versionCode.get())
        buildConfigField("int", "MODULE_VERSION", libs.versions.versionCode.get())
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", "false")
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    compileOnly(project(":smscode-core:smscode-xposed-core"))

    implementation(project(":xpbridge:core"))
    implementation(project(":relay:android"))
    implementation(project(":relay:contract"))
    implementation(project(":smscode-core:smscode-domain"))
    implementation(project(":smscode-core:smscode-verification-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.libxposed.service)
    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(project(":smscode-core:smscode-xposed-core"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
}
