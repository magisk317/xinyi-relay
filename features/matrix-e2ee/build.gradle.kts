plugins {
    id("com.android.dynamic-feature")
    id("relay.android.common")
}

android {
    namespace = "io.github.magisk317.relay.feature.matrix.e2ee"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            // jna.aar still ships deprecated Android ABIs that the base app no
            // longer publishes. BundleTool rejects the app bundle if the DFM
            // advertises more ABIs than the base module.
            excludes += setOf(
                "**/armeabi/*.so",
                "**/mips/*.so",
                "**/mips64/*.so",
            )
        }
    }
}

androidComponents {
    beforeVariants { variantBuilder ->
        val setTargetSdk = variantBuilder.javaClass.methods.firstOrNull {
            it.name == "setTargetSdk" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaObjectType))
        }
        checkNotNull(setTargetSdk) {
            "AGP DynamicFeatureVariantBuilder does not expose setTargetSdk"
        }.invoke(
            variantBuilder,
            // The platform spec may be a beta string ("37.2-beta3") when the shared
            // catalog wins; targetSdk must be the integer API level.
            libs.versions.targetSdk.get().substringBefore(".").toInt(),
        )

        // The base app attaches this DFM only for dedicated Play bundle/assemble
        // invocations or explicit :features:matrix_e2ee tasks (see
        // app/build.gradle.kts). When it stays detached no variant is consumed
        // by the base app, and root-level assembleDebug would still schedule
        // the Play manifest merge here, which fails on the empty feature
        // metadata ("Failed to find feature name"). Disable every variant in
        // that case using the same startParameter heuristic as the base app.
        val requestedTasks = gradle.startParameter.taskNames
        val requestedAppTasks = requestedTasks.map { it.substringAfterLast(":") }
        val requestsMatrixFeature = requestedTasks.any { it.contains(":features:matrix_e2ee") }
        val requestsPlayDynamicFeature = requestedAppTasks.any { taskName ->
            val normalized = taskName.substringAfterLast(":")
            normalized.contains("bundlePlay", ignoreCase = true) ||
                normalized.contains("packagePlay", ignoreCase = true) ||
                normalized.contains("assemblePlay", ignoreCase = true) ||
                normalized == "bundleRelease"
        }
        val requestsGithubVariant = requestedAppTasks.any { it.contains("Github", ignoreCase = true) }
        if (!((requestsPlayDynamicFeature || requestsMatrixFeature) && !requestsGithubVariant)) {
            // AGP 9 keeps VariantBuilder#enabled off the Kotlin-visible surface
            // for dynamic-feature builders; invoke the primitive setter the same
            // way the setTargetSdk call above already does.
            variantBuilder.javaClass.methods
                .firstOrNull { it.name == "setEnabled" && it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType)) }
                ?.invoke(variantBuilder, false)
        }
    }
}

dependencies {
    implementation(project(":app"))
    implementation(project(":relay:sender:api"))
    implementation(project(":relay:sender"))
    implementation(project(":relay:matrix-e2ee"))
}
