package com.github.magisk317.smscode.data.update

object UpdateCoordinator {

    enum class PlayAction {
        START_UPDATE_FLOW,
        OPEN_STORE_OR_GITHUB,
        NO_OP,
    }

    sealed class GithubManualAction {
        data object SHOW_CHECK_FAILED : GithubManualAction()
        data object SHOW_ALREADY_NEWEST : GithubManualAction()
        data class SHOW_UPDATE_DIALOG(val latest: GithubReleaseInfo) : GithubManualAction()
    }

    fun decidePlayAction(
        updateAvailable: Boolean,
        flexibleAllowed: Boolean,
        inProgress: Boolean,
        silentIfNoUpdate: Boolean,
    ): PlayAction = when {
        updateAvailable && flexibleAllowed -> PlayAction.START_UPDATE_FLOW
        inProgress -> PlayAction.START_UPDATE_FLOW
        updateAvailable -> PlayAction.OPEN_STORE_OR_GITHUB
        !silentIfNoUpdate -> PlayAction.OPEN_STORE_OR_GITHUB
        else -> PlayAction.NO_OP
    }

    fun decidePlayFailureAction(fallbackOnQueryFailure: Boolean): PlayAction =
        if (fallbackOnQueryFailure) PlayAction.OPEN_STORE_OR_GITHUB else PlayAction.NO_OP

    fun decideGithubManualAction(
        latest: GithubReleaseInfo?,
        currentVersion: String,
        showNoUpdateToast: Boolean,
    ): GithubManualAction? {
        if (latest == null) return GithubManualAction.SHOW_CHECK_FAILED
        if (GithubUpdateChecker.isNewer(currentVersion, latest.versionName)) {
            return GithubManualAction.SHOW_UPDATE_DIALOG(latest)
        }
        return if (showNoUpdateToast) GithubManualAction.SHOW_ALREADY_NEWEST else null
    }
}
