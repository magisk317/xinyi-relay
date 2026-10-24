package io.github.magisk317.relay.di

import io.github.magisk317.relay.mobileui.BuildConfig
import io.github.magisk317.relay.security.CiUpdateGateVault
import io.github.magisk317.relay.ui.backup.CloudBackupViewModel
import io.github.magisk317.relay.ui.record.CodeRecordViewModel
import io.github.magisk317.relay.ui.rule.RuleViewModel
import io.github.magisk317.relay.ui.home.forward.ForwardFilterViewModel
import io.github.magisk317.relay.ui.home.overview.OverviewViewModel
import io.github.magisk317.relay.ui.home.scheduled.ScheduledReminderViewModel
import io.github.magisk317.relay.ui.scheduled.ScheduledTaskViewModel
import io.github.magisk317.relay.ui.sender.SenderViewModel
import io.github.magisk317.uikit.update.AppUpdateChecker
import io.github.magisk317.uikit.update.AppUpdateController
import io.github.magisk317.uikit.update.AppUpdateDownloader
import io.github.magisk317.uikit.update.GitLabCiArtifactUpdateSource
import io.github.magisk317.uikit.update.GitLabReleaseUpdateSource
import io.github.magisk317.uikit.update.GitHubReleaseUpdateSource
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import io.github.magisk317.relay.ui.home.appconfig.AppConfigViewModel
import io.github.magisk317.relay.ui.home.settings.SettingsViewModel
import io.github.magisk317.relay.ui.setup.SetupViewModel

val uiModule = module {
    viewModelOf(::AppConfigViewModel)
    viewModelOf(::OverviewViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::CodeRecordViewModel)
    viewModelOf(::RuleViewModel)
    viewModelOf(::SenderViewModel)
    viewModelOf(::ForwardFilterViewModel)
    viewModelOf(::ScheduledReminderViewModel)
    viewModelOf(::ScheduledTaskViewModel)
    viewModelOf(::CloudBackupViewModel)
    viewModelOf(::SetupViewModel)

    single {
        val context = androidContext()
        val isCiOrDebug = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD

        val isPlayFlavor = BuildConfig.HAS_BILLING

        val sources = when {
            // Play flavor: version is owned by Play, no CI source
            isPlayFlavor -> listOf(
                GitLabReleaseUpdateSource("magisk3171/xinyi-relay"),
                GitHubReleaseUpdateSource(
                    repository = "magisk317/xinyi-relay",
                    gitlabProjectPath = "magisk3171/xinyi-relay",
                ),
            )

            isCiOrDebug -> listOf(
                GitLabCiArtifactUpdateSource(
                    projectPath = "magisk3171/xinyi-relay",
                    branch = "beta",
                    jobName = "android:build",
                    artifactPath = "ci-dist/app-ci.apk",
                    currentCommitHash = BuildConfig.COMMIT_HASH,
                ),
                GitLabReleaseUpdateSource("magisk3171/xinyi-relay"),
            )

            else -> listOf(
                GitLabReleaseUpdateSource("magisk3171/xinyi-relay"),
                GitHubReleaseUpdateSource(
                    repository = "magisk317/xinyi-relay",
                    gitlabProjectPath = "magisk3171/xinyi-relay",
                ),
            )
        }

        val checker = AppUpdateChecker(
            context = context,
            sources = sources,
        )
        val downloader = AppUpdateDownloader(context)
        AppUpdateController(
            context = context,
            checker = checker,
            downloader = downloader,
            fileProviderAuthority = "${context.packageName}.files",
            onForceUpdateDetected = if (isCiOrDebug) {
                {
                    // Gate logic: once a CI mandatory update is detected, seal the
                    // activation state and revoke privileges to block outdated builds.
                    CiUpdateGateVault.sealAndRevoke(context)
                }
            } else {
                // Release only surfaces a dialog on forced updates, never self-destructs
                {}
            },
        )
    }
}
