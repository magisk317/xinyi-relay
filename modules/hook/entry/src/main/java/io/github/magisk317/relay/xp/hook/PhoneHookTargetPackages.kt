package io.github.magisk317.relay.xp.hook

internal object PhoneHookTargetPackages {
    const val ANDROID_PHONE = "com.android.phone"
    const val XIAOMI_PHONE = "com.xiaomi.phone"

    val all: Set<String> = setOf(
        ANDROID_PHONE,
        XIAOMI_PHONE,
    )

    fun contains(packageName: String): Boolean = packageName in all

    fun describe(): String = all.joinToString(separator = ",")
}
