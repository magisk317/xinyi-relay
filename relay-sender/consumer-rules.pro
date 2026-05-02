# Sender config DTOs are persisted as Gson JSON in the app database and in backups.
# Keep field names stable for builds that write JSON without consulting @SerializedName.
-keepclassmembers class io.github.magisk317.relay.sender.config.** {
    <fields>;
}
