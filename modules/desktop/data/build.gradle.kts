plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.devtools.ksp")
}

kotlin {
    jvmToolchain(27)

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)
            // The snapshot export/import format (DatabaseTransfer) and the
            // @Serializable entities it round-trips both live in this module.
            api(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}
