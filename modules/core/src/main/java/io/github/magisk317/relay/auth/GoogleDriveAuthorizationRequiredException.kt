package io.github.magisk317.relay.auth

import android.content.Intent

class GoogleDriveAuthorizationRequiredException(
    val authorizationIntent: Intent,
) : Exception("Google Drive authorization required")
