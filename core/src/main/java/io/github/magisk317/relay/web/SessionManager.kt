package io.github.magisk317.relay.web

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class WebUiSession(
    val id: String,
    val username: String,
    val csrfToken: String,
    val createdAt: Long,
    var lastAccessAt: Long,
)

class SessionManager(
    private val sessionTtlMillis: Long = TimeUnit.HOURS.toMillis(24),
) {

    private val sessions = ConcurrentHashMap<String, WebUiSession>()

    fun create(username: String): WebUiSession {
        val now = System.currentTimeMillis()
        val session = WebUiSession(
            id = randomToken(48),
            username = username,
            csrfToken = randomToken(32),
            createdAt = now,
            lastAccessAt = now,
        )
        sessions[session.id] = session
        return session
    }

    fun get(sessionId: String?): WebUiSession? {
        if (sessionId.isNullOrBlank()) return null
        val session = sessions[sessionId] ?: return null
        val now = System.currentTimeMillis()
        if (now - session.lastAccessAt > sessionTtlMillis) {
            sessions.remove(sessionId)
            return null
        }
        session.lastAccessAt = now
        return session
    }

    fun destroy(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        sessions.remove(sessionId)
    }

    companion object {
        const val COOKIE_NAME = "relay_webui_session"
        const val COOKIE_MAX_AGE_SECONDS = 24 * 60 * 60

        private val secureRandom = SecureRandom()
        private const val tokenAlphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

        private fun randomToken(length: Int): String {
            return buildString(length) {
                repeat(length) {
                    append(tokenAlphabet[secureRandom.nextInt(tokenAlphabet.length)])
                }
            }
        }
    }
}
