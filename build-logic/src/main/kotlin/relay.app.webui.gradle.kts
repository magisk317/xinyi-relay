import org.gradle.api.tasks.Exec

val webUiDir = rootProject.layout.projectDirectory.dir("webui").asFile

val buildWebUi = tasks.register<Exec>("buildWebUi") {
    group = "webui"
    description = "Build the WebUI from webui/ into webui/dist."
    workingDir = webUiDir
    commandLine("pnpm", "build")
}

val syncWebUiAssets = tasks.register<Exec>("syncWebUiAssets") {
    group = "webui"
    description = "Sync webui/dist into app/src/main/assets/webui."
    workingDir = webUiDir
    commandLine("pnpm", "sync-dist")
    dependsOn(buildWebUi)
}

val checkWebUiAssets = tasks.register<Exec>("checkWebUiAssets") {
    group = "verification"
    description = "Verify embedded WebUI assets match the latest dist output."
    workingDir = webUiDir
    commandLine("pnpm", "check-dist")
    dependsOn(buildWebUi)
}

tasks.register("webuiBuild") {
    group = "webui"
    description = "Compatibility alias for buildWebUi."
    dependsOn(buildWebUi)
}

tasks.register("webuiSync") {
    group = "webui"
    description = "Compatibility alias for syncWebUiAssets."
    dependsOn(syncWebUiAssets)
}

pluginManager.withPlugin("com.android.application") {
    tasks.named("preBuild").configure {
        dependsOn(checkWebUiAssets)
    }
}
