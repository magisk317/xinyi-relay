package io.github.magisk317.relay.update

import io.github.magisk317.smscode.runtime.contract.update.GithubUpdateConfig

/**
 * Update endpoints for relay.
 *
 * Parsing, version comparison and ABI selection live in core. Callers pass this
 * config to core directly, so there is no per-host wrapper left to keep in sync.
 */
object GithubUpdateConfigHolder {
    val config = GithubUpdateConfig(
        latestReleaseApiUrl = "https://api.github.com/repos/magisk317/xinyi-relay/releases/latest",
        defaultReleaseHtmlUrl = "https://github.com/magisk317/xinyi-relay/releases/latest",
        userAgent = "XinyiRelay",
    )
}
