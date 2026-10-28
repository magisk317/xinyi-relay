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
import io.github.magisk317.relay.android.data.datasource.PreferenceDataSource
import io.github.magisk317.relay.contract.constant.RelayPrefConst
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import io.github.magisk317.uikit.update.AppUpdateSourceSpec
import io.github.magisk317.uikit.update.AppUpdateSources
import io.github.magisk317.uikit.update.CiStagingSpec
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

        // One shape for every consumer app: the channel (settings switch, else how the build was
        // produced) is re-read on each check, and a Play flavor never probes the CI track because
        // the store owns that version axis. See ui-kit's AppUpdateSources for the chain shape.
        val spec = AppUpdateSourceSpec(
            gitlabProjectPath = "magisk3171/xinyi-relay",
            githubRepository = "magisk317/xinyi-relay",
            ci = CiStagingSpec(
                artifactPath = "ci-dist/io.github.magisk317.xinyi.relay/{versionName}/app-ci.apk",
                versionSidecarPath = "ci-dist/io.github.magisk317.xinyi.relay/version.txt",
                manifestPath = "ci-dist/io.github.magisk317.xinyi.relay/{versionName}/app-ci-manifest.json",
            ),
            isPlayFlavor = { isPlayFlavor },
            buildProvenance = { isCiOrDebug },
            currentCommitHash = { BuildConfig.COMMIT_HASH },
            // Miss / Unavailable stay null so AppUpdateSources falls back to buildProvenance;
            // only an explicit switch value outranks how the build was produced.
            trackCiOverride = {
                when (val read = get<PreferenceDataSource>().readBoolean(RelayPrefConst.KEY_TRACK_CI_BUILDS)) {
                    is PrefRead.Hit -> read.value
                    else -> null
                }
            },
        )

        val checker = AppUpdateChecker(
            context = context,
            sources = AppUpdateSources.routed(spec),
            allowForceUpdate = !BuildConfig.SKIP_FORCE_UPDATE,
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
