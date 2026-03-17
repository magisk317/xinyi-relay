package io.github.magisk317.relay.common.utils

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object CallSessionTracker {
    private const val SESSION_TTL_MS = 120_000L
    private const val MAINLAND_CHINA_COUNTRY_CODE = "86"
    private const val MAINLAND_CHINA_MOBILE_LENGTH = 11
    private val activeSessions = ConcurrentHashMap<String, Long>()
    private val endedSessions = ConcurrentHashMap<String, Long>()

    data class Decision(
        val allow: Boolean,
        val stage: String,
        val key: String,
    )

    fun evaluate(
        stageRaw: String?,
        sender: String?,
        body: String?,
        callType: Int,
        packageName: String?,
    ): Decision {
        val stage = normalizeStage(stageRaw, callType)
        val number = extractPhoneNumber(sender, body)
        val direction = resolveDirection(callType)
        val key = buildKey(number, direction, packageName, sender, body)
        val now = System.currentTimeMillis()
        cleanup(now)
        return when (stage) {
            "ringing", "dialing", "ongoing" -> {
                val activeAt = activeSessions[key]
                if (activeAt != null && now - activeAt < SESSION_TTL_MS) {
                    Decision(false, stage, key)
                } else {
                    activeSessions[key] = now
                    endedSessions.remove(key)
                    Decision(true, stage, key)
                }
            }
            "ended" -> {
                val activeAt = activeSessions.remove(key)
                if (activeAt != null) {
                    endedSessions[key] = now
                    Decision(true, stage, key)
                } else {
                    val endedAt = endedSessions[key]
                    if (endedAt != null && now - endedAt < SESSION_TTL_MS) {
                        Decision(false, stage, key)
                    } else {
                        endedSessions[key] = now
                        Decision(true, stage, key)
                    }
                }
            }
            else -> Decision(true, stage, key)
        }
    }

    fun normalizeStage(stageRaw: String?, callType: Int): String {
        val stage = stageRaw?.trim().orEmpty()
        if (stage.isNotEmpty()) return stage
        return when (callType) {
            CALL_TYPE_INCOMING -> "ringing"
            CALL_TYPE_OUTGOING -> "dialing"
            CALL_TYPE_MISSED,
            CALL_TYPE_REJECTED,
            CALL_TYPE_BLOCKED,
            CALL_TYPE_VOICEMAIL,
            CALL_TYPE_ANSWERED_EXTERNALLY,
            -> "ended"
            else -> ""
        }
    }

    private fun resolveDirection(callType: Int): String {
        return when (callType) {
            CALL_TYPE_OUTGOING -> "out"
            CALL_TYPE_INCOMING,
            CALL_TYPE_MISSED,
            CALL_TYPE_REJECTED,
            CALL_TYPE_BLOCKED,
            CALL_TYPE_VOICEMAIL,
            CALL_TYPE_ANSWERED_EXTERNALLY,
            -> "in"
            else -> "unk"
        }
    }

    private fun extractPhoneNumber(sender: String?, body: String?): String {
        val combined = buildString {
            if (!sender.isNullOrBlank()) append(sender).append('\n')
            if (!body.isNullOrBlank()) append(body)
        }
        if (combined.isBlank()) return ""
        val matcher = PHONE_CANDIDATE_REGEX.findAll(combined)
        var best = ""
        for (match in matcher) {
            val normalized = normalizeDigits(match.value)
            if (normalized.length < 6) continue
            if (normalized.length > best.length) {
                best = normalized
            }
        }
        return best
    }

    private fun normalizeDigits(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.isBlank()) return ""
        return if (
            digits.startsWith(MAINLAND_CHINA_COUNTRY_CODE) &&
            digits.length > MAINLAND_CHINA_MOBILE_LENGTH
        ) {
            digits.removePrefix(MAINLAND_CHINA_COUNTRY_CODE)
        } else {
            digits
        }
    }

    private fun buildKey(
        number: String,
        direction: String,
        packageName: String?,
        sender: String?,
        body: String?,
    ): String {
        val pkg = packageName.orEmpty().lowercase(Locale.ROOT)
        val identity = if (number.isNotBlank()) {
            "num:$number"
        } else {
            val fallback = listOfNotNull(sender, body)
                .joinToString("|")
                .trim()
                .lowercase(Locale.ROOT)
                .replace("\\s+".toRegex(), " ")
                .take(64)
            "txt:$fallback"
        }
        return "$pkg|$direction|$identity"
    }

    fun buildSourceKey(
        sender: String?,
        body: String?,
        callType: Int,
        packageName: String?,
    ): String {
        val number = extractPhoneNumber(sender, body)
        val direction = resolveDirection(callType)
        return buildKey(number, direction, packageName, sender, body)
    }

    private fun cleanup(now: Long) {
        val cutoff = now - SESSION_TTL_MS
        activeSessions.entries.removeIf { it.value < cutoff }
        endedSessions.entries.removeIf { it.value < cutoff }
    }

    private val PHONE_CANDIDATE_REGEX = Regex("(\\+?\\d[\\d\\s\\-]{4,}\\d)")

    private const val CALL_TYPE_INCOMING = 1
    private const val CALL_TYPE_OUTGOING = 2
    private const val CALL_TYPE_MISSED = 3
    private const val CALL_TYPE_VOICEMAIL = 4
    private const val CALL_TYPE_REJECTED = 5
    private const val CALL_TYPE_BLOCKED = 6
    private const val CALL_TYPE_ANSWERED_EXTERNALLY = 7
}
