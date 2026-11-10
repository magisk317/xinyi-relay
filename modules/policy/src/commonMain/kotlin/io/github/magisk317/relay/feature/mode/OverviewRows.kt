package io.github.magisk317.relay.feature.mode

/** Xposed row on the overview page. */
enum class XposedRowState { ACTIVE, INACTIVE }

/** Notification-listener row on the overview page. Shown only in the non-root build. */
enum class ListenerRowState { CONNECTED, DISCONNECTED }

/** Shizuku row on the overview page. Shown only in the non-root build. */
enum class ShizukuRowState { AUTHORIZED, UNAUTHORIZED, UNREACHABLE }

/**
 * Rows of the overview runtime section. [listener] and [shizuku] are null when
 * the build has no non-root runtime, so the UI does not render those rows.
 */
data class OverviewRows(
    val xposed: XposedRowState,
    val listener: ListenerRowState?,
    val shizuku: ShizukuRowState?,
)

/**
 * Maps runtime signals and Shizuku status to the overview rows.
 *
 * The parameters take no LSPosed install state and no framework version, so the
 * rows cannot depend on either one.
 *
 * - Xposed: [xposedActive] selects ACTIVE or INACTIVE.
 * - Listener and Shizuku: produced only when [nonRootBuild] is true.
 * - Shizuku: UNREACHABLE when [shizukuOnline] is false. Otherwise
 *   [shizukuAuthorized] selects AUTHORIZED or UNAUTHORIZED.
 */
fun overviewRows(
    nonRootBuild: Boolean,
    xposedActive: Boolean,
    listenerConnected: Boolean,
    shizukuOnline: Boolean,
    shizukuAuthorized: Boolean,
): OverviewRows = OverviewRows(
    xposed = if (xposedActive) XposedRowState.ACTIVE else XposedRowState.INACTIVE,
    listener = when {
        !nonRootBuild -> null
        listenerConnected -> ListenerRowState.CONNECTED
        else -> ListenerRowState.DISCONNECTED
    },
    shizuku = when {
        !nonRootBuild -> null
        !shizukuOnline -> ShizukuRowState.UNREACHABLE
        shizukuAuthorized -> ShizukuRowState.AUTHORIZED
        else -> ShizukuRowState.UNAUTHORIZED
    },
)
