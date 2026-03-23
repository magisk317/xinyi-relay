import com.android.build.api.dsl.ApplicationExtension
import org.gradle.kotlin.dsl.configure

val keystoreFilePath = System.getenv("KEYSTORE_FILE")
    ?: project.findProperty("tianma.keystore.path")?.toString()
    ?: "release.jks"
val keyFile = project.file(keystoreFilePath)
val propertyFile = project.file(project.findProperty("tianma.signature.path") ?: "signature.properties")

val keyProps = java.util.Properties().apply {
    if (propertyFile.exists()) {
        propertyFile.inputStream().use(::load)
    }
}

val isSigningInfoAvailable = keyFile.exists() &&
    (
        keyProps.getProperty("STORE_PASSWORD") != null ||
            System.getenv("STORE_PASSWORD") != null
        )

pluginManager.withPlugin("com.android.application") {
    extensions.configure<ApplicationExtension> {
        val debugSigningConfig = signingConfigs.maybeCreate("debug").apply {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
        val releaseSigningConfig = signingConfigs.maybeCreate("release").apply {
            storeFile = keyFile
            storePassword = System.getenv("STORE_PASSWORD") ?: keyProps.getProperty("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS") ?: keyProps.getProperty("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD") ?: keyProps.getProperty("KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }

        buildTypes {
            getByName("debug") {
                buildConfigField("int", "LOG_LEVEL", "2")
                buildConfigField("boolean", "LOG_TO_XPOSED", "true")
                signingConfig = if (isSigningInfoAvailable) releaseSigningConfig else debugSigningConfig
            }
            maybeCreate("alpha").apply {
                isMinifyEnabled = true
                isShrinkResources = true
                isDebuggable = false

                buildConfigField("int", "LOG_LEVEL", "2")
                buildConfigField("boolean", "LOG_TO_XPOSED", "true")

                if (isSigningInfoAvailable) {
                    signingConfig = releaseSigningConfig
                }
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                )
                ndk {
                    debugSymbolLevel = "FULL"
                }
            }
            getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true

                buildConfigField("int", "LOG_LEVEL", "4")
                buildConfigField("boolean", "LOG_TO_XPOSED", "true")
                signingConfig = if (isSigningInfoAvailable) releaseSigningConfig else debugSigningConfig
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                )
                ndk {
                    debugSymbolLevel = "FULL"
                }
                lint {
                    disable.add("MissingTranslation")
                    checkReleaseBuilds = false
                }
            }
        }
    }
}
