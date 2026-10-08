package io.github.magisk317.relay.auth

import android.app.PendingIntent

class GoogleDriveAuthorizationRequiredException(
    val pendingIntent: PendingIntent,
) : Exception("Google Drive authorization required")
