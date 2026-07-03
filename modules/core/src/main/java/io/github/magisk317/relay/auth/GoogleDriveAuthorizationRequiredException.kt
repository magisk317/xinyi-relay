package io.github.magisk317.relay.auth

import android.content.Intent

class GoogleDriveAuthorizationRequiredException(
    val authorizationIntent: Intent,
    cause: Throwable? = null,
) : Exception("Google Drive authorization required", cause)
