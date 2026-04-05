package io.github.magisk317.relay.common.utils

import android.content.Context
import android.os.SystemClock
import io.github.magisk317.relay.diagnostics.RuntimeLogEntry
import io.github.magisk317.relay.diagnostics.RuntimeLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FrameworkCompatibilityMonitor {

    enum class FrameworkIssueType {
        KNOWN_INCOMPATIBLE_FRAMEWORK,
        HOOKER_ANNOTATION_INCOMPATIBLE,
    }

    data class FrameworkIssue(
        val issueType: FrameworkIssueType,
        val message: String,
        val detectedAt: Long,
        val frameworkInfo: FrameworkInfo? = null,
    )

    private const val ANNOTATION_ERROR_TEXT = "Hooker should be annotated with @XposedHooker"

    private val _issue = MutableStateFlow<FrameworkIssue?>(null)
    val issueState: StateFlow<FrameworkIssue?> = _issue.asStateFlow()

    fun resolveFrameworkInfo(context: Context): FrameworkInfo? = FrameworkInfoResolver.resolve(context)

    fun inspect(context: Context): FrameworkIssue? {
        val frameworkInfo = resolveFrameworkInfo(context)
        val matchedLog = findAnnotationFailureLog()
        return detectIssue(
            frameworkInfo = frameworkInfo,
            latestLogMessage = matchedLog?.message,
            detectedAt = matchedLog?.timestamp ?: System.currentTimeMillis(),
        )
    }

    fun refresh(context: Context) {
        _issue.value = inspect(context)
    }

    internal fun clearForTesting() {
        _issue.value = null
    }

    internal fun detectIssue(
        frameworkInfo: FrameworkInfo?,
        latestLogMessage: String?,
        detectedAt: Long,
    ): FrameworkIssue? {
        if (frameworkInfo != null && isKnownIncompatibleFramework(frameworkInfo)) {
            return FrameworkIssue(
                issueType = FrameworkIssueType.KNOWN_INCOMPATIBLE_FRAMEWORK,
                message = frameworkInfo.displayLabel,
                detectedAt = detectedAt,
                frameworkInfo = frameworkInfo,
            )
        }
        if (!latestLogMessage.isNullOrBlank() && latestLogMessage.contains(ANNOTATION_ERROR_TEXT)) {
            return FrameworkIssue(
                issueType = FrameworkIssueType.HOOKER_ANNOTATION_INCOMPATIBLE,
                message = latestLogMessage,
                detectedAt = detectedAt,
                frameworkInfo = frameworkInfo,
            )
        }
        return null
    }

    private fun findAnnotationFailureLog(): RuntimeLogEntry? {
        val now = System.currentTimeMillis()
        val bootStartAt = (now - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        return RuntimeLogStore.query(minutes = null, keyword = null, limit = 2000)
            .lastOrNull { entry ->
                entry.timestamp >= bootStartAt &&
                    entry.message.contains(ANNOTATION_ERROR_TEXT)
            }
    }

    private fun isKnownIncompatibleFramework(frameworkInfo: FrameworkInfo): Boolean {
        val tokens = listOfNotNull(
            frameworkInfo.name,
            frameworkInfo.moduleId,
            frameworkInfo.author,
        ).joinToString(" ").lowercase()
        return tokens.contains("jingmatrix") ||
            tokens.contains("zygisk_vector") ||
            tokens.contains(" vector")
    }
}
