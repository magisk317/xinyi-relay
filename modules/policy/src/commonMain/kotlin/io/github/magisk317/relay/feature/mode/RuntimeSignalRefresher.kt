package io.github.magisk317.relay.feature.mode

/**
 * Name and version of the Xposed framework that the app reports.
 *
 * The values are already normalized by the kit's `FrameworkIdentityResolver`:
 * a blank value or the placeholder `unknown` arrives here as `null`.
 */
data class FrameworkIdentity(val name: String?, val version: String?)

/**
 * Re-reads the runtime signals behind the overview page and the feature gates.
 *
 * The implementation lives in the app module, the only module that depends on
 * the framework service module. `:policy` receives the results as plain values.
 */
interface RuntimeSignalRefresher {
    /**
     * Re-reads every signal that an earlier read may have left stale.
     * Safe to call on every resume.
     */
    suspend fun refresh()

    /**
     * Framework name and version. Uses the bound framework service when it is
     * connected, otherwise the values recorded at the last bind.
     */
    fun frameworkIdentity(): FrameworkIdentity
}
