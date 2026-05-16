# Sender config DTOs are persisted as JSON in the app database and in backups.
# Keep field names stable across releases.
-keepclassmembers class io.github.magisk317.relay.sender.config.** {
    <fields>;
}
