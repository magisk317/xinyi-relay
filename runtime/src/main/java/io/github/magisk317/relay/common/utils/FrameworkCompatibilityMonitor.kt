package io.github.magisk317.relay.common.utils

import android.os.SystemClock
import io.github.magisk317.relay.diagnostics.RuntimeLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FrameworkCompatibilityMonitor {

    enum class FrameworkIssueType {
        HOOKER_ANNOTATION_INCOMPATIBLE,
    }

    data class FrameworkIssue(
        val issueType: FrameworkIssueType,
        val message: String,
        val detectedAt: Long,
    )

    private const val ANNOTATION_ERROR_TEXT = "Hooker should be annotated with @XposedHooker"

    private val _issue = MutableStateFlow<FrameworkIssue?>(null)
    val issueState: StateFlow<FrameworkIssue?> = _issue.asStateFlow()

    fun refreshFromRuntimeLogs() {
        val now = System.currentTimeMillis()
        val bootStartAt = (now - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val matched = RuntimeLogStore.query(minutes = null, keyword = null, limit = 2000)
            .lastOrNull { entry ->
                entry.timestamp >= bootStartAt &&
                    entry.message.contains(ANNOTATION_ERROR_TEXT)
            }
        _issue.value = matched?.let { entry ->
            FrameworkIssue(
                issueType = FrameworkIssueType.HOOKER_ANNOTATION_INCOMPATIBLE,
                message = entry.message,
                detectedAt = entry.timestamp,
            )
        }
    }

    internal fun clearForTesting() {
        _issue.value = null
    }
}
