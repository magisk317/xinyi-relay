plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(project(":magisk-ui-kit"))
            implementation(project(":relay:contract"))
            implementation(project(":relay:net"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.magisk317.relay.desktop.MainKt"
    }
}
