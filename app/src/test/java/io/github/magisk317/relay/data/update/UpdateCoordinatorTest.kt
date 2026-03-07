package io.github.magisk317.relay.data.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UpdateCoordinatorTest {

    @Test
    fun decidePlayAction_coversMainBranches() {
        assertEquals(
            UpdateCoordinator.PlayAction.START_UPDATE_FLOW,
            UpdateCoordinator.decidePlayAction(
                updateAvailable = true,
                flexibleAllowed = true,
                inProgress = false,
                silentIfNoUpdate = true,
            ),
        )
        assertEquals(
            UpdateCoordinator.PlayAction.START_UPDATE_FLOW,
            UpdateCoordinator.decidePlayAction(
                updateAvailable = false,
                flexibleAllowed = false,
                inProgress = true,
                silentIfNoUpdate = true,
            ),
        )
        assertEquals(
            UpdateCoordinator.PlayAction.OPEN_STORE_OR_GITHUB,
            UpdateCoordinator.decidePlayAction(
                updateAvailable = true,
                flexibleAllowed = false,
                inProgress = false,
                silentIfNoUpdate = true,
            ),
        )
        assertEquals(
            UpdateCoordinator.PlayAction.OPEN_STORE_OR_GITHUB,
            UpdateCoordinator.decidePlayAction(
                updateAvailable = false,
                flexibleAllowed = false,
                inProgress = false,
                silentIfNoUpdate = false,
            ),
        )
        assertEquals(
            UpdateCoordinator.PlayAction.NO_OP,
            UpdateCoordinator.decidePlayAction(
                updateAvailable = false,
                flexibleAllowed = false,
                inProgress = false,
                silentIfNoUpdate = true,
            ),
        )
    }

    @Test
    fun decidePlayFailureAction_respectsFallbackFlag() {
        assertEquals(UpdateCoordinator.PlayAction.OPEN_STORE_OR_GITHUB, UpdateCoordinator.decidePlayFailureAction(true))
        assertEquals(UpdateCoordinator.PlayAction.NO_OP, UpdateCoordinator.decidePlayFailureAction(false))
    }

    @Test
    fun decideGithubManualAction_returnsExpectedActions() {
        val latest = GithubReleaseInfo("3.1.2", "https://github.com/magisk317/xinyi-relay/releases/tag/v3.1.2")

        assertEquals(
            UpdateCoordinator.GithubManualAction.SHOW_CHECK_FAILED,
            UpdateCoordinator.decideGithubManualAction(
                latest = null,
                currentVersion = "3.1.1",
                showNoUpdateToast = true,
            ),
        )

        val action = UpdateCoordinator.decideGithubManualAction(
            latest = latest,
            currentVersion = "3.1.1",
            showNoUpdateToast = true,
        )
        assertEquals(UpdateCoordinator.GithubManualAction.SHOW_UPDATE_DIALOG(latest), action)

        assertEquals(
            UpdateCoordinator.GithubManualAction.SHOW_ALREADY_NEWEST,
            UpdateCoordinator.decideGithubManualAction(
                latest = GithubReleaseInfo("3.1.1", latest.htmlUrl),
                currentVersion = "3.1.1",
                showNoUpdateToast = true,
            ),
        )

        assertNull(
            UpdateCoordinator.decideGithubManualAction(
                latest = GithubReleaseInfo("3.1.1", latest.htmlUrl),
                currentVersion = "3.1.1",
                showNoUpdateToast = false,
            ),
        )
    }
}
